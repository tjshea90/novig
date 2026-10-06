// node genprompts.js <workflow_files_analysis.js> <v0701 dir>
// Writes <v0701 dir>/prompts/<label>.txt (one per Phase-1 analyst) plus _preamble.txt, _strategy_angles.json, _lenses.json, _schemas.json
// for the Phase 2-4 agents, parsing the constants out of the workflow script (the Workflow tool is not always available).
const fs = require('fs')
const [src, sp] = process.argv.slice(2)
let code = fs.readFileSync(src, 'utf8')
const lenses = code.slice(code.indexOf('const LENSES = ['), code.indexOf("phase('Verify')"))
code = code.slice(0, code.indexOf('// ---------------------------------------------------------------- run')).replace(/^export const meta/m, 'const meta') + '\n' + lenses
const body = code + '\nreturn {PREAMBLE, STUDY_TOOLS, DIAG_TOOLS, DIAG_DIMS, EXPLORE_DIMS, STRATEGY_ANGLES, LENSES, FINDINGS_SCHEMA, STUDY_SCHEMA, RULE_SCHEMA, VERDICT_SCHEMA}'
const m = new Function('args', body)({ sp })
const out = sp + '/prompts/'
fs.mkdirSync(out, { recursive: true })
const REPO_COPY = `\nSAVING (this overrides the READ-ONLY rule for exactly ONE file): you may write a second copy of your final structured result, as JSON, to /home/user/novig/research/v0701_partial/<your label>.json (create the directory if missing; write NOTHING else into the repo). That copy is public on GitHub: it holds numbers and findings only: no bet rows dumps, no wallet balance, no keys, no account or user ids. Do it as your LAST step, after writing ${sp}/work/<your label>/result.json. Your final chat reply must be a summary of at most 400 words with your 10 most important numbers.\n`
for (const d of m.DIAG_DIMS) fs.writeFileSync(out + d.label + '.txt', m.PREAMBLE + m.DIAG_TOOLS + '\n' + d.prompt + REPO_COPY + '\nRESULT SCHEMA (write JSON that matches it):\n' + JSON.stringify(m.FINDINGS_SCHEMA, null, 1))
for (const d of m.EXPLORE_DIMS) fs.writeFileSync(out + d.label + '.txt', m.PREAMBLE + m.STUDY_TOOLS + '\n' + d.prompt + REPO_COPY + '\nRESULT SCHEMA (write JSON that matches it):\n' + JSON.stringify(m.STUDY_SCHEMA, null, 1))
fs.writeFileSync(out + '_preamble.txt', m.PREAMBLE + m.STUDY_TOOLS)
fs.writeFileSync(out + '_strategy_angles.json', JSON.stringify(m.STRATEGY_ANGLES, null, 1))
fs.writeFileSync(out + '_lenses.json', JSON.stringify(m.LENSES, null, 1))
fs.writeFileSync(out + '_schemas.json', JSON.stringify({ RULE_SCHEMA: m.RULE_SCHEMA, VERDICT_SCHEMA: m.VERDICT_SCHEMA }, null, 1))
console.log('wrote', fs.readdirSync(out).length, 'files to', out)
