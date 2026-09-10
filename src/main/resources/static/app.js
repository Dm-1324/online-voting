const API = '/api/polls';
let voterName = localStorage.getItem('voterName') || '';
let isLoading = false;
let firstLoad = true;
let toastTimer;

const $ = id => document.getElementById(id);

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

function setStatus(message = '') {
  $('status').textContent = message;
}

function showToast(message) {
  const toast = $('toast');
  toast.textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2600);
}

async function apiRequest(url, options = {}) {
  const response = await fetch(url, options);
  let data = null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    data = await response.json().catch(() => null);
  }
  if (!response.ok) {
    throw new Error(data?.error || data?.message || `Request failed (${response.status})`);
  }
  return data;
}

function renderPoll(poll) {
  const totalVotes = poll.options.reduce((sum, option) => sum + Number(option.voteCount || 0), 0);
  const voterInput = poll.open
    ? `<input class="voter-input" id="voter-${poll.id}" maxlength="80" autocomplete="name" placeholder="Enter your name to vote" value="${escapeHtml(voterName)}" aria-label="Your name for ${escapeHtml(poll.question)}">`
    : '';

  const optionsHtml = poll.options.map(option => {
    const votes = Number(option.voteCount || 0);
    const pct = totalVotes ? Math.round((votes / totalVotes) * 100) : 0;
    return `<div class="option-row">
      <div class="option-line"><span>${escapeHtml(option.text)}</span><span class="option-count">${votes} · ${pct}%</span></div>
      <div class="bar-bg" aria-label="${pct}% of votes"><div class="bar-fill" style="width:${pct}%"></div></div>
      ${poll.open ? `<div class="option-actions"><button class="vote-btn" data-action="vote" data-poll="${poll.id}" data-option="${option.id}" type="button">Vote for this</button></div>` : ''}
    </div>`;
  }).join('');

  return `<article class="poll" data-poll-id="${poll.id}">
    <div class="poll-header">
      <div><h3>${escapeHtml(poll.question)} ${!poll.open ? '<span class="closed-tag">CLOSED</span>' : ''}</h3><p class="poll-meta">${poll.open ? 'Voting is open' : 'Voting has ended'} · Poll #${poll.id}</p></div>
    </div>
    ${voterInput}
    <div class="options">${optionsHtml}</div>
    <div class="poll-footer"><span class="total-votes">${totalVotes} ${totalVotes === 1 ? 'vote' : 'votes'} total</span>${poll.open ? `<button class="close-btn" data-action="close" data-poll="${poll.id}" type="button">Close poll</button>` : '<span class="total-votes">Results locked</span>'}</div>
    <div class="error" id="error-${poll.id}" role="alert"></div>
  </article>`;
}

async function loadPolls({silent = false} = {}) {
  if (isLoading) return;
  isLoading = true;
  if (!silent) setStatus('Loading polls…');
  try {
    const polls = await apiRequest(API);
    // Critical UI fix: never rebuild the input that currently has focus while a user is typing.
    // The old 3-second innerHTML refresh destroyed the voter field and caused characters to disappear.
    const active = document.activeElement;
    const activePollId = active?.classList?.contains('voter-input') ? active.id.replace('voter-', '') : null;
    const activeValue = activePollId ? active.value : null;
    $('polls').innerHTML = polls.map(renderPoll).join('');
    $('emptyState').hidden = polls.length !== 0;
    if (activePollId && activeValue !== null) {
      const restored = $(`voter-${activePollId}`);
      if (restored) {
        restored.value = activeValue;
        restored.focus({preventScroll: true});
        try { restored.setSelectionRange(activeValue.length, activeValue.length); } catch (_) {}
      }
    }
    setStatus(`${polls.length} ${polls.length === 1 ? 'poll' : 'polls'} · updates automatically`);
    firstLoad = false;
  } catch (error) {
    setStatus('Unable to load polls. Check that the server is running.');
    if (firstLoad) {
      $('polls').innerHTML = `<div class="empty-state"><div class="empty-icon">!</div><h3>Couldn’t connect</h3><p>${escapeHtml(error.message)}</p></div>`;
    }
  } finally {
    isLoading = false;
  }
}

async function vote(pollId, optionId) {
  const nameInput = $(`voter-${pollId}`);
  const errorEl = $(`error-${pollId}`);
  if (!nameInput) return;
  voterName = nameInput.value.trim();
  errorEl.textContent = '';
  if (!voterName) {
    nameInput.focus();
    errorEl.textContent = 'Please enter your name before voting.';
    return;
  }
  if (voterName.length > 80) {
    errorEl.textContent = 'Name must be 80 characters or fewer.';
    return;
  }
  localStorage.setItem('voterName', voterName);
  const buttons = document.querySelectorAll(`[data-action="vote"][data-poll="${pollId}"]`);
  buttons.forEach(button => button.disabled = true);
  try {
    await apiRequest(`${API}/${pollId}/vote`, {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({voterName, optionId})
    });
    showToast('Vote recorded successfully ✓');
    await loadPolls({silent: true});
  } catch (error) {
    errorEl.textContent = error.message;
    buttons.forEach(button => button.disabled = false);
  }
}

async function closePoll(pollId) {
  if (!confirm('Close this poll? No more votes will be accepted.')) return;
  try {
    await apiRequest(`${API}/${pollId}/close`, {method: 'POST'});
    showToast('Poll closed. Results are now locked.');
    await loadPolls({silent: true});
  } catch (error) {
    const errorEl = $(`error-${pollId}`);
    if (errorEl) errorEl.textContent = error.message;
  }
}

$('polls').addEventListener('click', event => {
  const button = event.target.closest('button[data-action]');
  if (!button) return;
  const pollId = Number(button.dataset.poll);
  if (button.dataset.action === 'vote') vote(pollId, Number(button.dataset.option));
  if (button.dataset.action === 'close') closePoll(pollId);
});

$('polls').addEventListener('input', event => {
  if (event.target.classList.contains('voter-input')) {
    voterName = event.target.value;
    localStorage.setItem('voterName', voterName);
  }
});

$('createForm').addEventListener('submit', async event => {
  event.preventDefault();
  const question = $('question').value.trim();
  const opt1 = $('opt1').value.trim();
  const opt2 = $('opt2').value.trim();
  $('createError').textContent = '';
  if (!question || !opt1 || !opt2) {
    $('createError').textContent = 'Please fill in the question and both options.';
    return;
  }
  const button = $('createBtn');
  button.disabled = true;
  button.querySelector('span').textContent = 'Creating…';
  try {
    await apiRequest(API, {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({question, options: [opt1, opt2]})
    });
    event.target.reset();
    showToast('Poll created successfully ✓');
    await loadPolls({silent: true});
  } catch (error) {
    $('createError').textContent = error.message;
  } finally {
    button.disabled = false;
    button.querySelector('span').textContent = 'Create poll';
  }
});

$('refreshBtn').addEventListener('click', () => loadPolls());
loadPolls();
// Refresh results without interrupting a name being typed.
setInterval(() => loadPolls({silent: true}), 5000);
