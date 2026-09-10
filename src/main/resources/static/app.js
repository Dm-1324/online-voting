const API = '/api/polls';
let voterName = localStorage.getItem('voterName') || '';

async function loadPolls() {
  const res = await fetch(API);
  const polls = await res.json();
  document.getElementById('polls').innerHTML = polls.map(renderPoll).join('');
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
}

function renderPoll(poll) {
  const total = poll.options.reduce((s, o) => s + o.voteCount, 0) || 1;
  const optionsHtml = poll.options.map(o => {
    const pct = Math.round((o.voteCount / total) * 100);
    return `<div class="option-row"><div class="bar-bg"><div class="bar-fill" style="width:${pct}%"></div><span class="bar-label">${escapeHtml(o.text)} — ${o.voteCount} votes (${pct}%)</span></div>${poll.open ? `<button class="vote-btn" onclick="vote(${poll.id}, ${o.id})">Vote</button>` : ''}</div>`;
  }).join('');
  return `<div class="poll"><h3>${escapeHtml(poll.question)} ${!poll.open ? '<span class="closed-tag">CLOSED</span>' : ''}</h3>${poll.open ? `<input class="voter-input" id="voter-${poll.id}" placeholder="Your name" value="${escapeHtml(voterName)}">` : ''}${optionsHtml}${poll.open ? `<button onclick="closePoll(${poll.id})">Close Poll</button>` : ''}<div class="error" id="error-${poll.id}"></div></div>`;
}

async function vote(pollId, optionId) {
  const nameInput = document.getElementById('voter-' + pollId);
  voterName = nameInput.value.trim();
  if (!voterName) { alert('Enter your name first'); return; }
  localStorage.setItem('voterName', voterName);
  const res = await fetch(`${API}/${pollId}/vote`, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ voterName, optionId }) });
  const errorEl = document.getElementById('error-' + pollId);
  if (!res.ok) { const err = await res.json(); errorEl.textContent = err.error || 'Vote failed'; } else { errorEl.textContent = ''; }
  loadPolls();
}

async function closePoll(pollId) {
  await fetch(`${API}/${pollId}/close`, { method: 'POST' });
  loadPolls();
}

document.getElementById('createForm').addEventListener('submit', async e => {
  e.preventDefault();
  const question = document.getElementById('question').value;
  const opt1 = document.getElementById('opt1').value;
  const opt2 = document.getElementById('opt2').value;
  const res = await fetch(API, { method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({ question, options: [opt1, opt2] }) });
  if (!res.ok) alert('Could not create poll');
  e.target.reset();
  loadPolls();
});

loadPolls();
setInterval(loadPolls, 3000);
