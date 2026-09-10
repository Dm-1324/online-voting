const API = '/api/polls';
const VOTER_KEY = 'voteflow.voterId';
const NAME_KEY = 'voteflow.voterName';
const ADMIN_KEY = 'voteflow.adminTokens';

let voterName = localStorage.getItem(NAME_KEY) || '';
let voterId = localStorage.getItem(VOTER_KEY);
let adminTokens = JSON.parse(localStorage.getItem(ADMIN_KEY) || '{}');
let isLoading = false;
let firstLoad = true;
let toastTimer;

if (!voterId) {
  voterId = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  localStorage.setItem(VOTER_KEY, voterId);
}

const $ = id => document.getElementById(id);
const sharedCode = location.pathname.match(/^\/p\/([A-Za-z0-9]+)\/?$/)?.[1] || null;

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

function showToast(message) {
  const toast = $('toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2800);
}

async function apiRequest(url, options = {}) {
  const response = await fetch(url, { ...options, cache: 'no-store' });
  const type = response.headers.get('content-type') || '';
  const data = type.includes('application/json') ? await response.json().catch(() => null) : null;
  if (!response.ok) throw new Error(data?.error || data?.message || `Request failed (${response.status})`);
  return data;
}

function totalVotes(poll) {
  return poll.options.reduce((sum, option) => sum + Number(option.voteCount || 0), 0);
}

function renderPoll(poll) {
  const total = totalVotes(poll);
  const adminToken = adminTokens[poll.shareCode];
  const isOwner = Boolean(adminToken);
  const publicUrl = `${location.origin}/p/${encodeURIComponent(poll.shareCode)}`;

  const optionsHtml = poll.options.map(option => {
    const votes = Number(option.voteCount || 0);
    const pct = total ? Math.round((votes / total) * 100) : 0;
    return `<div class="option-row">
      <div class="option-line"><span>${escapeHtml(option.text)}</span><span class="option-count">${votes} · ${pct}%</span></div>
      <div class="bar-bg"><div class="bar-fill" style="width:${pct}%"></div></div>
      ${poll.open ? `<button class="vote-btn" data-action="vote" data-poll="${poll.id}" data-option="${option.id}" type="button">Vote for this</button>` : ''}
    </div>`;
  }).join('');

  return `<article class="poll" data-poll-id="${poll.id}">
    <div class="poll-header">
      <div>
        <div class="poll-status ${poll.open ? 'open' : 'closed'}"><span></span>${poll.open ? 'LIVE NOW' : 'CLOSED'}</div>
        <h3>${escapeHtml(poll.question)}</h3>
        <p class="poll-meta">${total} ${total === 1 ? 'vote' : 'votes'} · Share code ${escapeHtml(poll.shareCode)}</p>
      </div>
      <button class="copy-btn" data-action="copy" data-url="${escapeHtml(publicUrl)}" type="button">Copy link</button>
    </div>

    ${poll.open ? `<div class="voter-field"><label for="voter-${poll.id}">Your name</label><input class="voter-input" id="voter-${poll.id}" maxlength="80" autocomplete="name" placeholder="Enter your name to vote" value="${escapeHtml(voterName)}"></div>` : ''}
    <div class="options">${optionsHtml}</div>
    <div class="poll-footer">
      <span class="total-votes">${poll.open ? 'Results update automatically' : 'Results locked'}</span>
      ${isOwner && poll.open ? `<button class="close-btn" data-action="close" data-poll="${poll.id}" type="button">Close poll</button>` : ''}
    </div>
    <div class="error" id="error-${poll.id}" role="alert"></div>
  </article>`;
}

async function loadPolls({silent = false} = {}) {
  if (isLoading) return;
  isLoading = true;
  if (!silent) $('status').textContent = 'Loading…';
  try {
    const polls = sharedCode
      ? [await apiRequest(`${API}/public/${encodeURIComponent(sharedCode)}`)]
      : await apiRequest(API);

    const active = document.activeElement;
    const activeId = active?.classList?.contains('voter-input') ? active.id : null;
    const activeValue = activeId ? active.value : null;

    $('polls').innerHTML = polls.map(renderPoll).join('');
    $('emptyState').hidden = polls.length > 0;

    if (activeId && activeValue !== null) {
      const restored = $(activeId);
      if (restored) {
        restored.value = activeValue;
        restored.focus({preventScroll: true});
        try { restored.setSelectionRange(activeValue.length, activeValue.length); } catch (_) {}
      }
    }

    $('status').textContent = sharedCode ? 'Live poll · updates automatically' : `${polls.length} ${polls.length === 1 ? 'poll' : 'polls'} · live updates`;
    if (sharedCode) {
      $('createSection').hidden = true;
      $('pollHeading').textContent = 'Vote now';
      $('pollEyebrow').textContent = 'SHARED POLL';
    }
    firstLoad = false;
  } catch (error) {
    $('status').textContent = 'Unable to load this poll.';
    if (firstLoad) $('polls').innerHTML = `<div class="empty-state"><div class="empty-icon">!</div><h3>Couldn’t load poll</h3><p>${escapeHtml(error.message)}</p></div>`;
  } finally {
    isLoading = false;
  }
}

async function vote(pollId, optionId) {
  const input = $(`voter-${pollId}`);
  const error = $(`error-${pollId}`);
  if (!input) return;
  const name = input.value.trim();
  error.textContent = '';
  if (!name) { input.focus(); error.textContent = 'Enter your name before voting.'; return; }

  document.querySelectorAll(`[data-action="vote"][data-poll="${pollId}"]`).forEach(b => b.disabled = true);
  try {
    await apiRequest(`${API}/${pollId}/vote`, {
      method: 'POST',
      headers: {'Content-Type': 'application/json', 'X-Voter-Id': voterId},
      body: JSON.stringify({voterName: name, optionId})
    });
    voterName = name;
    localStorage.setItem(NAME_KEY, voterName);
    showToast('Vote recorded successfully');
    await loadPolls({silent: true});
  } catch (e) {
    error.textContent = e.message;
    document.querySelectorAll(`[data-action="vote"][data-poll="${pollId}"]`).forEach(b => b.disabled = false);
  }
}

async function closePoll(pollId) {
  const poll = document.querySelector(`[data-poll-id="${pollId}"]`);
  const code = poll?.querySelector('.poll-meta')?.textContent.match(/Share code (\w+)/)?.[1];
  const token = code ? adminTokens[code] : null;
  if (!token) { showToast('Only the poll creator can close this poll'); return; }
  if (!confirm('Close this poll? No more votes will be accepted.')) return;
  try {
    await apiRequest(`${API}/${pollId}/close`, {method: 'POST', headers: {'X-Poll-Admin-Token': token}});
    showToast('Poll closed and results locked');
    await loadPolls({silent: true});
  } catch (e) {
    const error = $(`error-${pollId}`);
    if (error) error.textContent = e.message;
  }
}

async function copyLink(url) {
  try { await navigator.clipboard.writeText(url); showToast('Poll link copied'); }
  catch (_) { prompt('Copy this poll link:', url); }
}

$('polls').addEventListener('click', event => {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const pollId = Number(button.dataset.poll);
  if (button.dataset.action === 'vote') vote(pollId, Number(button.dataset.option));
  if (button.dataset.action === 'close') closePoll(pollId);
  if (button.dataset.action === 'copy') copyLink(button.dataset.url);
});

$('polls').addEventListener('input', event => {
  if (event.target.classList.contains('voter-input')) {
    voterName = event.target.value;
    localStorage.setItem(NAME_KEY, voterName);
  }
});

$('createForm').addEventListener('submit', async event => {
  event.preventDefault();
  const question = $('question').value.trim();
  const opt1 = $('opt1').value.trim();
  const opt2 = $('opt2').value.trim();
  $('createError').textContent = '';
  if (!question || !opt1 || !opt2) { $('createError').textContent = 'Please complete all fields.'; return; }

  const button = $('createBtn');
  button.disabled = true;
  button.querySelector('.btn-label').textContent = 'Creating…';
  try {
    const result = await apiRequest(API, {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({question, options: [opt1, opt2]})
    });
    adminTokens[result.poll.shareCode] = result.adminToken;
    localStorage.setItem(ADMIN_KEY, JSON.stringify(adminTokens));
    event.target.reset();
    showToast('Poll created — your private creator key is saved on this device');
    await loadPolls({silent: true});
    setTimeout(() => copyLink(`${location.origin}/p/${result.poll.shareCode}`), 250);
  } catch (e) {
    $('createError').textContent = e.message;
  } finally {
    button.disabled = false;
    button.querySelector('.btn-label').textContent = 'Create poll';
  }
});

$('refreshBtn').addEventListener('click', () => loadPolls());

if (sharedCode) {
  $('createSection').hidden = true;
  $('homeLink').setAttribute('href', '/');
} else {
  $('homeLink').setAttribute('href', '#');
}

loadPolls();
setInterval(() => loadPolls({silent: true}), 5000);
