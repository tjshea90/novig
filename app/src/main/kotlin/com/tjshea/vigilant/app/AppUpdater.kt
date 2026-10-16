package com.tjshea.vigilant.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tjshea.vigilant.data.update.AppUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Updates from GitHub (Tj, 2026-10-16: "make it so GitHub/Claude can push updates directly to the vigilant android app. When an update is available, I get a push notification and an install update button which
 * automatically downloads the update from GitHub and updates the app just like other Android apps"). Every release Claude publishes (`release.yml` -> the Release page's signed APK) is found here: [check] reads the
 * latest Release (a free public call; the saved GitHub token is added when there is one, so a private repo works), a notification with an "Install update" button says so once per version, and [install] downloads the
 * APK and hands it to Android's PackageInstaller, which verifies the signature is the installed app's and replaces it. Android asks "Install unknown apps" once for Vigilant; after the first update Vigilant is the
 * installer of record and (Android 12+) later updates install without another confirmation. A phone cannot be pushed to without Google's servers, so the notification comes from [UpdateWorker] (every 15 minutes
 * while there is a network, app open or not) and from each time the app opens.
 */
class AppUpdater(private val c: AppContainer) {
    data class State(
        val latest: AppUpdate? = null,
        val checkedAtMs: Long = 0L,
        /** A newer release than the running app exists. */
        val available: Boolean = false,
        /** Download progress 0..1 while downloading; null otherwise. */
        val progress: Float? = null,
        val installing: Boolean = false,
        /** Android must be told it may install apps from Vigilant (a one-time switch). */
        val needsPermission: Boolean = false,
        val note: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    @Volatile private var retriedLoud = false
    private val prefs get() = c.app.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    /** Reads the newest Release; posts the notification (once per version) when it is newer than this app. Null when it could not be read. */
    suspend fun check(notify: Boolean): AppUpdate? = withContext(Dispatchers.IO) {
        val token = c.cloud.githubToken()
        val req = Request.Builder().url("https://api.github.com/repos/$REPO/releases/latest").header("Accept", "application/vnd.github+json").apply { token?.let { header("Authorization", "Bearer $it") } }.build()
        val body = try {
            c.http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else { _state.update { it.copy(note = "GitHub answered ${r.code} when asked for updates") }; null } }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(note = "Could not reach GitHub for updates") }; null }
        val latest = body?.let(AppUpdate::parse) ?: return@withContext null
        val newer = AppUpdate.isNewer(latest, BuildConfig.VERSION_NAME)
        _state.update { it.copy(latest = latest, checkedAtMs = System.currentTimeMillis(), available = newer, note = if (newer) null else "Vigilant ${BuildConfig.VERSION_NAME} is the newest release.", needsPermission = newer && !canInstall()) }
        if (newer && notify && prefs.getString("notified", "") != latest.tag) {
            if (postNotification(latest)) prefs.edit().putString("notified", latest.tag).apply()
        }
        if (!newer) runCatching { NotificationManagerCompat.from(c.app).cancel(ID_UPDATE) }
        latest
    }

    fun canInstall(): Boolean = c.app.packageManager.canRequestPackageInstalls()

    /** The screen where Android lets Tj allow installs from Vigilant (one switch, once). */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads the newest APK and installs it. Safe to call twice at once (the second waits and finds nothing to do). */
    suspend fun install() = lock.withLock {
        val latest = _state.value.latest?.takeIf { _state.value.available } ?: check(notify = false)?.takeIf { AppUpdate.isNewer(it, BuildConfig.VERSION_NAME) } ?: run {
            _state.update { it.copy(note = "No newer release to install.") }; return@withLock
        }
        if (!canInstall()) {
            _state.update { it.copy(needsPermission = true, note = "Allow Vigilant to install apps once, then tap Install update again.") }
            notifyPlain("Allow installs from Vigilant", "Android needs one switch before Vigilant can install ${latest.tag}. Tap to open it.", PendingIntent.getActivity(c.app, 1, permissionIntent(), PendingIntent.FLAG_IMMUTABLE))
            return@withLock
        }
        val file = File(c.app.cacheDir, "update/vigilant-${latest.tag}.apk").apply { parentFile?.mkdirs() }
        try {
            _state.update { it.copy(progress = 0f, note = "Downloading ${latest.tag}…") }
            download(latest, file)
            _state.update { it.copy(progress = null, installing = true, note = "Installing ${latest.tag}…") }
            // Copying the APK into the installer session (and fsync) is disk work: off the main thread (the freeze in Tj's screenshot, "Vigilant isn't responding").
            withContext(Dispatchers.IO) { commit(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            file.delete()
            val why = e.message?.take(120) ?: e.javaClass.simpleName
            _state.update { it.copy(progress = null, installing = false, note = "Update failed: $why") }
            notifyPlain("Update failed", why, EvAlerts.openVigilant(c.app, ID_UPDATE))
        }
    }

    /**
     * The APK is megabytes: the app's shared client gives every call 30 s in total and 20 s of silence, which cut a slow download off as "timeout" (Tj, 2026-10-16 screenshot: "Update failed: timeout" at a third of the
     * progress bar). This client has no total limit (a stall of 60 s still ends a try), and a try that fails half way carries on from the bytes already on disk (Range), up to 4 tries.
     */
    private suspend fun download(u: AppUpdate, file: File) = withContext(Dispatchers.IO) {
        val token = c.cloud.githubToken()
        val client = c.http.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        file.delete()
        var attempt = 0
        while (true) {
            attempt++
            try {
                downloadTry(client, token, u, file)
                return@withContext
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                if (attempt >= 4) throw e
                _state.update { it.copy(note = "Download interrupted (${e.message?.take(60) ?: e.javaClass.simpleName}); continuing, try ${attempt + 1} of 4…") }
                delay(1_500L * attempt)
            }
        }
    }

    private fun downloadTry(client: okhttp3.OkHttpClient, token: String?, u: AppUpdate, file: File) {
        val have = if (file.exists()) file.length() else 0L
        // With a token the API asset URL works for a private repo; without one the public download URL does. OkHttp drops the Authorization header on the redirect to the file host.
        val rb = if (token != null) Request.Builder().url(u.apkApiUrl).header("Accept", "application/octet-stream").header("Authorization", "Bearer $token") else Request.Builder().url(u.apkUrl)
        if (have > 0) rb.header("Range", "bytes=$have-")
        client.newCall(rb.build()).execute().use { r ->
            if (!r.isSuccessful) error("GitHub answered ${r.code} for the APK")
            val body = r.body ?: error("empty download")
            // 206 = the server continued from [have]; a plain 200 sent the whole file again.
            val resumed = r.code == 206 && have > 0
            var done = if (resumed) have else 0L
            val total = (if (resumed) have + body.contentLength() else body.contentLength()).takeIf { it > 0 } ?: u.apkBytes
            var shown = -1
            var lastMs = 0L
            java.io.FileOutputStream(file, resumed).use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); done += n
                        // Progress at most every 250 ms and whole percents: a state change per 64 KB chunk recomposed the screen dozens of times a second.
                        val now = System.currentTimeMillis()
                        val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                        if (total > 0 && pct != shown && now - lastMs >= 250) {
                            shown = pct; lastMs = now
                            _state.update { it.copy(progress = (done.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            }
            if (u.apkBytes > 0 && done != u.apkBytes) throw java.io.IOException("the download was cut short ($done of ${u.apkBytes} bytes)")
        }
    }

    /** [quiet]: ask Android to install with no confirmation when it may (an update by the installer of record); false = the usual confirmation screen, where Play Protect offers "Install anyway". */
    private fun commit(file: File, quiet: Boolean = true) {
        // The update kills this process: the receiver of the new version's start-up finds this stamp and brings the app back in front (Tj, 2026-10-16: restart the app by itself after an update).
        prefs.edit().putLong("installing_since", System.currentTimeMillis()).apply()
        val installer = c.app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(c.app.packageName)
            if (quiet && Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input -> session.openWrite("vigilant.apk", 0, file.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val pi = PendingIntent.getBroadcast(c.app, id, Intent(c.app, UpdateStatusReceiver::class.java).setPackage(c.app.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pi.intentSender)
        }
    }

    /** Called by [UpdateStatusReceiver] with the installer's verdict. */
    fun installerSaid(status: Int, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants one tap (the first update, or a phone that always asks): in front of Tj if the app is open, else a notification that opens the prompt.
                val i = confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                if (c.onScreen) runCatching { c.app.startActivity(i) }
                else notifyPlain("Tap to finish updating Vigilant", "Android needs one confirmation.", PendingIntent.getActivity(c.app, 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            }
            PackageInstaller.STATUS_SUCCESS -> {
                retriedLoud = false
                _state.update { it.copy(installing = false, available = false, note = "Updated.") }
                File(c.app.cacheDir, "update").deleteRecursively()
            }
            else -> {
                // A refusal of the quiet install (Play Protect's verification, "Install not allowed", Tj 2026-10-16) is tried once more the ordinary way, with Android's own confirmation screen, where
                // Play Protect's "Install anyway" is offered. The downloaded file is kept for it.
                val file = File(c.app.cacheDir, "update").listFiles()?.firstOrNull { it.name.endsWith(".apk") }
                if (!retriedLoud && file != null) {
                    retriedLoud = true
                    _state.update { it.copy(installing = true, note = "Android refused the quiet install (${message ?: "status $status"}); asking again with its own screen…") }
                    runCatching { commit(file, quiet = false) }.onFailure { _state.update { s -> s.copy(installing = false, note = "Update failed: ${it.message}") } }
                } else {
                    retriedLoud = false
                    _state.update { it.copy(installing = false, note = "Android would not install it: ${message ?: "status $status"}") }
                    notifyPlain("Update not installed", (message ?: "Android refused the update (status $status).") + " If Play Protect blocked it, open Play Store > Play Protect > settings, or tap Install anyway when it asks.", EvAlerts.openVigilant(c.app, ID_UPDATE))
                }
            }
        }
    }

    private fun postNotification(u: AppUpdate): Boolean {
        if (!ScanService.canNotify(c.app)) return false
        return runCatching {
            channel()
            val install = PendingIntent.getBroadcast(c.app, 3, Intent(c.app, UpdateActionReceiver::class.java).setAction(ACTION_INSTALL).setPackage(c.app.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(c.app, CHANNEL)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle("Vigilant ${u.version} is ready")
                .setContentText("You have ${BuildConfig.VERSION_NAME}. Tap Install update.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("You have ${BuildConfig.VERSION_NAME}.\n" + u.notes.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(200)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(EvAlerts.openVigilant(c.app, ID_UPDATE))
                .withWallet(c.app)
                .addAction(0, "Install update", install)
                .build()
            NotificationManagerCompat.from(c.app).notify(ID_UPDATE, n)
            true
        }.getOrDefault(false)
    }

    private fun notifyPlain(title: String, text: String, pi: PendingIntent) {
        if (!ScanService.canNotify(c.app)) return
        runCatching {
            channel()
            NotificationManagerCompat.from(c.app).notify(ID_UPDATE + 1, NotificationCompat.Builder(c.app, CHANNEL).setSmallIcon(R.drawable.ic_scan).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(pi).withWallet(c.app).build())
        }
    }

    private fun channel() {
        c.app.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_HIGH).apply { description = "A new Vigilant release is ready to install." })
    }

    companion object {
        const val REPO = "tjshea90/novig"
        const val CHANNEL = "app_update"
        const val ID_UPDATE = 7720
        const val ACTION_INSTALL = "com.tjshea.vigilant.action.INSTALL_UPDATE"
    }
}

/** The notification's "Install update" button: downloads and installs with no screen. */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppUpdater.ACTION_INSTALL) return
        val c = (context.applicationContext as VigilantApp).container
        val pending = goAsync()
        c.appScope.launch { try { c.updater.install() } finally { pending.finish() } }
    }
}

/** The installer's answer ([PackageInstaller]); on success Android restarts the app, so the receiver mostly reports failures and the one-tap confirmation. */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as VigilantApp).container
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        @Suppress("DEPRECATION")
        val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
        c.updater.installerSaid(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE), confirm)
    }
}

/** Looks for a new release every 15 minutes (WorkManager's shortest), whether the app is open or not. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as VigilantApp).container
        runCatching { c.updater.check(notify = true) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("app-update-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/**
 * The new version's first moments (Tj, 2026-10-16: "when I manually press update inside the app, it automatically closes the app ... make it restart the app automatically"): Android ends the old process when it
 * replaces the app, so this runs in the NEW one. If an update this app started was under way (the stamp [AppUpdater] left), the app is opened again; where Android does not let a background app open a screen it says
 * "Vigilant updated" in a notification that opens it. (The "Display over other apps" permission the widget uses is what lets the first work.)
 */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)
        val since = prefs.getLong("installing_since", 0L)
        if (since == 0L || System.currentTimeMillis() - since > 20 * 60_000L) return
        prefs.edit().remove("installing_since").putString("notified", "").apply()
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val started = runCatching { context.startActivity(open); true }.getOrDefault(false)
        // Always leave the note: a background start may be silently dropped, and the note is the way back in.
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(AppUpdater.CHANNEL, "App updates", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(context, 4, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            if (ScanService.canNotify(context)) {
                nm.notify(AppUpdater.ID_UPDATE + 2, NotificationCompat.Builder(context, AppUpdater.CHANNEL).setSmallIcon(R.drawable.ic_scan).setContentTitle("Vigilant updated to ${BuildConfig.VERSION_NAME}")
                    .setContentText(if (started) "Opened for you." else "Tap to open it.").setAutoCancel(true).setContentIntent(pi).withWallet(context).build())
            }
        }
    }
}
