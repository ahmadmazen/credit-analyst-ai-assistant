'use strict';

const el = id => document.getElementById(id);
const applicantSelect = el('applicant-select');
const productSelect = el('product-select');
const profileCard = el('profile-card');
const runBtn = el('run-btn');
const runStatus = el('run-status');
const timeline = el('timeline');
const decisionCard = el('decision-card');
const errorBanner = el('error-banner');
const rawDetails = el('raw-details');
const rawJson = el('raw-json');

const rulesToggle = el('rules-toggle');
const rulesPanel = el('rules-panel');
const rulesTabs = el('rules-tabs');
const rulesContent = el('rules-content');
const rulesLegend = el('rules-legend');

let applicants = [];
let eventSource = null;
let currentRunId = null;
let rulesData = null;      // fetched lazily from /api/rules
let activeRulesTab = null;
let citedRuleKeys = [];    // normalized cited rules from the latest decision
const openModelCards = {}; // "agent:turn" -> card element
const toolCards = {};      // tool_call id -> card element

function fmtMoney(n) {
    return '$' + Number(n).toLocaleString('en-US');
}

// All dynamic strings (LLM output included) are escaped before being placed in
// HTML. Quotes are encoded too, so values are safe inside attributes as well.
function esc(value) {
    return String(value == null ? '' : value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

async function loadScenarios() {
    const res = await fetch('/api/scenarios');
    const data = await res.json();
    applicants = data.applicants;
    applicantSelect.innerHTML = applicants
        .map(a => `<option value="${esc(a.id)}">${esc(a.id)} — ${esc(a.name)}</option>`).join('');
    productSelect.innerHTML = data.products
        .map(p => `<option value="${esc(p)}">${esc(p)}</option>`).join('');
    renderProfile();
}

function renderProfile() {
    const a = applicants.find(x => x.id === applicantSelect.value);
    if (!a) { profileCard.innerHTML = ''; return; }
    profileCard.innerHTML = `
        <div class="profile-name">${esc(a.name)} <span class="profile-age">age ${esc(a.age)}</span></div>
        <div class="profile-grid">
            <div><span>Credit score</span><b>${esc(a.creditScore)}</b></div>
            <div><span>Annual income</span><b>${fmtMoney(a.annualIncome)}</b></div>
            <div><span>Existing debt</span><b>${fmtMoney(a.totalExistingDebt)}</b></div>
            <div><span>Loan requested</span><b>${fmtMoney(a.requestedLoanAmount)}</b></div>
            <div><span>Vehicle value</span><b>${fmtMoney(a.vehicleAssessedValue)}</b></div>
        </div>
        <p class="profile-desc">${esc(a.description)}</p>`;
}

function setStatus(text, cls) {
    runStatus.textContent = text;
    runStatus.className = 'run-status ' + cls;
}

function showError(message) {
    errorBanner.textContent = message;
    errorBanner.classList.remove('hidden');
}

function resetRunView() {
    currentRunId = null;
    citedRuleKeys = [];
    if (rulesContent) {
        rulesContent.querySelectorAll('.rule-line.cited').forEach(l => l.classList.remove('cited'));
    }
    if (rulesLegend) { rulesLegend.classList.add('hidden'); }
    timeline.innerHTML = '';
    decisionCard.classList.add('hidden');
    errorBanner.classList.add('hidden');
    rawDetails.classList.add('hidden');
    Object.keys(openModelCards).forEach(k => delete openModelCards[k]);
    Object.keys(toolCards).forEach(k => delete toolCards[k]);
}

function agentLabel(agent) {
    return agent === 'document_analyst' ? 'Document Analyst (sub-agent)' : 'Credit Analyst';
}

// Finalize any model card still showing a spinner (run failed or was cut short).
function stopThinkingCards() {
    Object.entries(openModelCards).forEach(([key, card]) => {
        if (card.classList.contains('thinking')) {
            const [agent] = key.split(':');
            card.classList.remove('thinking');
            card.querySelector('.card-head').innerHTML = `
                <span class="badge ${agent === 'document_analyst' ? 'badge-sub' : 'badge-main'}">${agentLabel(agent)}</span>
                <em>stopped</em>`;
        }
    });
}

function addCard(cls, html, nested) {
    const card = document.createElement('div');
    card.className = 'event-card ' + cls + (nested ? ' nested' : '');
    card.innerHTML = html;
    timeline.appendChild(card);
    timeline.scrollTop = timeline.scrollHeight;
    return card;
}

function prettyArgs(args) {
    try { return esc(JSON.stringify(args, null, 1).replace(/\n\s*/g, ' ')); }
    catch { return esc(String(args)); }
}

const handlers = {
    run_started(p) {
        addCard('info', `<b>Run started</b> — ${esc(p.applicantName)} (${esc(p.applicantId)}), ${esc(p.productType)}`);
    },
    model_call(p) {
        const nested = p.agent === 'document_analyst';
        const card = addCard('model thinking', `
            <div class="card-head">
                <span class="badge ${nested ? 'badge-sub' : 'badge-main'}">${agentLabel(p.agent)}</span>
                <span class="spinner"></span> <em>${agentLabel(p.agent)} is thinking… (turn ${p.turn})</em>
            </div>`, nested);
        openModelCards[p.agent + ':' + p.turn] = card;
    },
    model_completed(p) {
        const card = openModelCards[p.agent + ':' + p.turn];
        const secs = (p.durationMs / 1000).toFixed(1);
        const what = p.action === 'tool_calls' ? 'requested tools' : 'returned the final answer';
        if (card) {
            card.classList.remove('thinking');
            card.querySelector('.card-head').innerHTML = `
                <span class="badge ${p.agent === 'document_analyst' ? 'badge-sub' : 'badge-main'}">${agentLabel(p.agent)}</span>
                thought for ${secs}s and ${what}`;
        }
    },
    tool_call(p) {
        toolCards[p.id] = addCard('tool', `
            <div class="card-head"><span class="badge badge-tool">tool</span>
                <b>${esc(p.name)}</b><code class="args">${prettyArgs(p.arguments)}</code></div>
            <div class="tool-result pending">running…</div>`);
    },
    tool_result(p) {
        const card = toolCards[p.id];
        const body = `<details><summary>result</summary><pre>${esc(p.result)}</pre></details>`;
        if (card) {
            card.querySelector('.tool-result').outerHTML = `<div class="tool-result">${body}</div>`;
        } else {
            addCard('tool', `<div class="card-head"><span class="badge badge-tool">tool</span>
                <b>${esc(p.name)}</b></div><div class="tool-result">${body}</div>`);
        }
    },
    guideline_lookup(p) {
        addCard('info nested', `<b>Guideline lookup</b> — reading <code>${esc(p.file)}</code>
            to answer: <em>"${esc(p.question)}"</em>`);
    },
    decision(p) {
        renderDecision(p);
        updateCitedRules(p);
    },
    run_completed(p) {
        stopThinkingCards();
        const secs = (p.durationMs / 1000).toFixed(0);
        addCard('info', `<b>Run ${p.status.toLowerCase()}</b> in ${secs}s`);
        finishRun(p.status === 'COMPLETED' ? 'Completed' : 'Failed',
            p.status === 'COMPLETED' ? 'done' : 'failed');
    },
    error(p) {
        stopThinkingCards();
        showError(p.message);
    }
};

function renderDecision(d) {
    const decision = d.decision || 'UNKNOWN';
    const cls = { APPROVE: 'approve', REJECT: 'reject', REVIEW: 'review' }[decision] || 'unknown';
    const metrics = d.metrics || {};
    const chips = [
        metrics.creditScore != null ? `<span class="chip">Score <b>${esc(metrics.creditScore)}</b></span>` : '',
        metrics.dtiPercent != null ? `<span class="chip">DTI <b>${Number(metrics.dtiPercent).toFixed(1)}%</b></span>` : '',
        metrics.ltvPercent != null ? `<span class="chip">LTV <b>${Number(metrics.ltvPercent).toFixed(1)}%</b></span>` : '',
        metrics.annualIncome != null ? `<span class="chip">Income <b>${fmtMoney(metrics.annualIncome)}</b></span>` : ''
    ].join('');
    const steps = (d.reasoningSteps || []).map(s => `
        <li class="step ${esc((s.outcome || 'INFO').toLowerCase())}">
            <span class="step-outcome">${s.outcome === 'PASS' ? '✓' : s.outcome === 'FAIL' ? '✗' : 'ℹ'}</span>
            <span>${esc(s.step)}${s.ruleCited ? `<span class="rule-cite">${esc(s.ruleCited)}</span>` : ''}</span>
        </li>`).join('');
    const rules = (d.citedRules || []).map(r => `<li>${esc(r)}</li>`).join('');
    decisionCard.className = 'decision-card ' + cls;
    decisionCard.innerHTML = `
        <div class="decision-head">
            <span class="decision-label">${esc(decision)}</span>
            <span class="decision-summary">${esc(d.summary || '')}</span>
        </div>
        <div class="chips">${chips}</div>
        ${steps ? `<h3>Reasoning</h3><ul class="steps">${steps}</ul>` : ''}
        ${rules ? `<h3>Rules applied</h3><ul class="cited-rules">${rules}</ul>` : ''}`;
    decisionCard.classList.remove('hidden');
    decisionCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function finishRun(label, cls) {
    setStatus(label, cls);
    runBtn.disabled = false;
    if (eventSource) { eventSource.close(); eventSource = null; }
    if (currentRunId) {
        rawDetails.classList.remove('hidden');
        fetch('/api/analysis/' + currentRunId)
            .then(r => r.json())
            .then(data => { rawJson.textContent = JSON.stringify(data, null, 2); })
            .catch(() => { });
    }
}

async function runAnalysis() {
    runBtn.disabled = true;
    resetRunView();
    setStatus('Running…', 'running');
    try {
        const res = await fetch('/api/analysis', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                applicantId: applicantSelect.value,
                productType: productSelect.value
            })
        });
        if (!res.ok) {
            const problem = await res.json().catch(() => ({}));
            throw new Error(problem.detail || ('Request failed with HTTP ' + res.status));
        }
        const { runId } = await res.json();
        currentRunId = runId;
        listen(runId);
    } catch (e) {
        showError(e.message);
        finishRun('Failed', 'failed');
    }
}

function listen(runId) {
    eventSource = new EventSource('/api/analysis/' + runId + '/events');
    Object.keys(handlers).forEach(type => {
        eventSource.addEventListener(type, ev => handlers[type](JSON.parse(ev.data)));
    });
    eventSource.onerror = () => {
        // EventSource retries on its own; if the run already finished, just close.
        if (runStatus.classList.contains('done') || runStatus.classList.contains('failed')) {
            eventSource?.close();
        }
    };
}

// ---------- Rules panel (side-by-side with the analysis) ----------

const POLICY_TAB = 'General Policy';

async function toggleRules() {
    const opening = rulesPanel.classList.contains('hidden');
    rulesPanel.classList.toggle('hidden', !opening);
    document.querySelector('.layout').classList.toggle('rules-open', opening);
    rulesToggle.classList.toggle('active', opening);
    if (opening && !rulesData) {
        try {
            rulesData = await (await fetch('/api/rules')).json();
        } catch (e) {
            rulesContent.innerHTML = '<p class="placeholder">Could not load rules.</p>';
            return;
        }
        renderRulesTabs();
    }
    if (opening) {
        selectRulesTab(activeRulesTab || productSelect.value || POLICY_TAB);
    }
}

function renderRulesTabs() {
    const tabs = [POLICY_TAB, ...Object.keys(rulesData.products)];
    rulesTabs.innerHTML = tabs.map(t =>
        `<button class="rules-tab" data-tab="${esc(t)}">${esc(t)}</button>`).join('');
    rulesTabs.querySelectorAll('.rules-tab').forEach(btn =>
        btn.addEventListener('click', () => selectRulesTab(btn.dataset.tab)));
}

function selectRulesTab(tab) {
    if (!rulesData) { return; }
    if (tab !== POLICY_TAB && !rulesData.products[tab]) { tab = POLICY_TAB; }
    activeRulesTab = tab;
    rulesTabs.querySelectorAll('.rules-tab').forEach(btn =>
        btn.classList.toggle('active', btn.dataset.tab === tab));
    const text = tab === POLICY_TAB ? rulesData.policy : rulesData.products[tab];
    rulesContent.innerHTML = text.split('\n').map(line => {
        const trimmed = line.trim();
        if (!trimmed) { return ''; }
        if (trimmed.startsWith('***')) {
            return `<div class="rules-doc-title">${esc(trimmed.replace(/\*/g, '').trim())}</div>`;
        }
        const ruleText = trimmed.replace(/^-\s*/, '');
        return `<div class="rule-line" data-norm="${esc(normalizeRule(ruleText))}">${esc(ruleText)}</div>`;
    }).join('');
    applyRuleHighlights();
}

function normalizeRule(text) {
    return String(text).toLowerCase().replace(/[^a-z0-9%$]+/g, ' ').trim();
}

// Marks rule lines that the decision cited (in citedRules or reasoning steps).
function applyRuleHighlights() {
    const lines = rulesContent.querySelectorAll('.rule-line');
    let any = false;
    lines.forEach(lineEl => {
        const norm = lineEl.dataset.norm;
        const cited = citedRuleKeys.some(key =>
            key.length > 12 && norm.length > 12 && (norm.includes(key) || key.includes(norm)));
        lineEl.classList.toggle('cited', cited);
        any = any || cited;
    });
    rulesLegend.classList.toggle('hidden', !citedRuleKeys.length);
}

function updateCitedRules(decision) {
    const cites = [
        ...(decision.citedRules || []),
        ...((decision.reasoningSteps || []).map(s => s.ruleCited).filter(Boolean))
    ];
    citedRuleKeys = cites.map(normalizeRule).filter(k => k.length > 12);
    if (!rulesPanel.classList.contains('hidden')) {
        applyRuleHighlights();
    }
}

// Changing the scenario clears the previous run's output so old results are
// never mistaken for the newly selected applicant's outcome.
function onScenarioChange() {
    renderProfile();
    if (!runBtn.disabled) { // don't wipe the view of a run in progress
        resetRunView();
        timeline.innerHTML = '<p class="placeholder">Scenario changed — run the analysis to see this applicant\'s result.</p>';
        setStatus('Idle', 'idle');
    }
    // keep the rules panel in step with the selected product
    if (!rulesPanel.classList.contains('hidden') && rulesData) {
        selectRulesTab(productSelect.value);
    }
}

applicantSelect.addEventListener('change', onScenarioChange);
productSelect.addEventListener('change', onScenarioChange);
rulesToggle.addEventListener('click', toggleRules);
runBtn.addEventListener('click', runAnalysis);
loadScenarios().catch(e => showError('Could not load scenarios: ' + e.message));
