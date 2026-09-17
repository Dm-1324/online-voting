const API='/api/polls';
const VOTER_KEY='voteflow.voterId';
const NAME_KEY='voteflow.voterName';
const CREATOR_AUTH='voteflow.creatorAuth';
const ADMIN_AUTH='voteflow.adminAuth';
const MAX_OPTIONS=10;

let voterName=localStorage.getItem(NAME_KEY)||'';
let voterId=localStorage.getItem(VOTER_KEY);
let mode='public';
let loginKind='admin';
let toastTimer;
let creatorAuth=JSON.parse(sessionStorage.getItem(CREATOR_AUTH)||'null');
let adminToken=sessionStorage.getItem(ADMIN_AUTH)||null;

if(!voterId){
  voterId=crypto.randomUUID?crypto.randomUUID():`${Date.now()}-${Math.random().toString(36).slice(2)}`;
  localStorage.setItem(VOTER_KEY,voterId);
}

const $=id=>document.getElementById(id);
const sharedCode=location.pathname.match(/^\/p\/([A-Za-z0-9]+)\/?$/)?.[1]||null;

function escapeHtml(value){
  return String(value).replace(/[&<>'"]/g,character=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[character]));
}

function showToast(message){
  const toast=$('toast');
  toast.textContent=message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer=setTimeout(()=>toast.classList.remove('show'),2800);
}

async function apiRequest(url,options={}){
  const response=await fetch(url,{...options,cache:'no-store'});
  const type=response.headers.get('content-type')||'';
  const data=type.includes('application/json')?await response.json().catch(()=>null):null;
  if(!response.ok){
    throw new Error(data?.message||data?.error||`Request failed (${response.status})`);
  }
  return data;
}

function totalVotes(poll){
  return poll.options.reduce((sum,option)=>sum+Number(option.voteCount||0),0);
}

function renderOptions(poll,management=false){
  const total=totalVotes(poll);
  return poll.options.map(option=>{
    const votes=Number(option.voteCount||0);
    const percentage=total?Math.round(votes/total*100):0;
    const names=Array.isArray(option.voterNames)?option.voterNames:[];
    const opinions=Array.isArray(option.customOpinions)?option.customOpinions:[];
    const voterList=names.length?names.map(name=>`<li>${escapeHtml(name)}</li>`).join(''):'<li>No voters yet</li>';
    const opinionList=opinions.length?`<strong>Other opinions</strong><ul>${opinions.map(opinion=>`<li>${escapeHtml(opinion)}</li>`).join('')}</ul>`:'';
    const voteButton=poll.open&&!management?`<button class="action-btn vote-btn" data-action="vote" data-poll="${poll.id}" data-option="${option.id}">Vote for this</button>`:'';
    const otherInput=option.other&&!management&&poll.open?`<input class="other-input" data-other-input="${poll.id}" maxlength="200" placeholder="Write your own opinion or option…" aria-label="Your Other option">`:'';
    return `<div class="option-row"><div class="option-line"><span>${escapeHtml(option.text)}</span><span class="option-count voter-details" tabindex="0">${votes} · ${percentage}%<span class="voter-tooltip"><strong>Voters</strong><ul>${voterList}</ul>${opinionList}</span></span></div><div class="bar-bg"><div class="bar-fill" style="width:${percentage}%"></div></div>${otherInput}${voteButton}</div>`;
  }).join('');
}

function managementActions(poll){
  if(!poll.open){
    return '<span class="closed-label">Poll closed</span>';
  }
  return `<span class="manage-actions"><button class="action-btn edit-btn" data-action="edit" data-poll="${poll.id}">Edit poll</button><button class="action-btn close-btn" data-action="close" data-poll="${poll.id}">Close poll</button>${mode==='admin'?`<button class="action-btn delete-btn" data-action="delete" data-poll="${poll.id}">Delete poll</button>`:''}</span>`;
}

function renderPoll(poll,management=false){
  const total=totalVotes(poll);
  const url=`${location.origin}/p/${encodeURIComponent(poll.shareCode)}`;
  const copyButton=!management?`<button class="action-btn copy-btn" data-action="copy" data-url="${escapeHtml(url)}">Copy link</button>`:'';
  const voterField=!management&&poll.open?`<div class="voter-field"><label for="voter-${poll.id}">Your name</label><input id="voter-${poll.id}" class="voter-input" data-voter="${poll.id}" maxlength="80" autocomplete="name" placeholder="Enter your name to vote" value="${escapeHtml(voterName)}"></div>`:'';
  const footerText=management?'Management view · Other is available to voters':poll.open?'Results update automatically · Hover a result to see voters':'Results locked · Hover a result to see voters';
  const actions=management?managementActions(poll):'';
  return `<article class="poll" data-poll-id="${poll.id}"><div class="poll-header"><div><div class="poll-status ${poll.open?'open':'closed'}"><span></span>${poll.open?'LIVE NOW':'CLOSED'}</div><h3>${escapeHtml(poll.question)}</h3><p class="poll-meta">${total} ${total===1?'vote':'votes'} · Share code ${escapeHtml(poll.shareCode)}</p></div>${copyButton}</div>${voterField}<div class="options">${renderOptions(poll,management)}</div><div class="poll-footer"><span class="total-votes">${footerText}</span>${actions}</div><div class="error" id="error-${poll.id}"></div></article>`;
}

function renderCreateOptions(){
  const container=$('optionFields');
  const currentValues=[...container.querySelectorAll('.option-input')].map(input=>input.value);
  container.innerHTML='';
  const count=Math.max(2,currentValues.length);
  for(let index=0;index<count;index++){
    const field=document.createElement('div');
    field.className='field option-field';
    field.innerHTML=`<div class="option-label-row"><label for="opt${index+1}">Option ${index+1}</label>${index>=2?`<button class="remove-option" type="button" data-remove-option="${index}">Remove</button>`:''}</div><input id="opt${index+1}" class="option-input" maxlength="100" autocomplete="off" placeholder="Option ${index+1}" required>`;
    container.appendChild(field);
    if(currentValues[index]){
      field.querySelector('input').value=currentValues[index];
    }
  }
  updateOptionControls();
}

function updateOptionControls(){
  const count=document.querySelectorAll('.option-input').length;
  $('optionLimit').textContent=`${count} option${count===1?'':'s'} · maximum ${MAX_OPTIONS}`;
  $('addOptionBtn').disabled=count>=MAX_OPTIONS;
  $('addOptionBtn').textContent=count>=MAX_OPTIONS?'✓ Maximum options reached':'＋ Add option';
}

function addOption(){
  const inputs=[...document.querySelectorAll('.option-input')];
  if(inputs.length>=MAX_OPTIONS){
    showToast('A poll can have up to 10 options.');
    return;
  }
  const values=inputs.map(input=>input.value);
  values.push('');
  $('optionFields').innerHTML='';
  values.forEach((value,index)=>{
    const field=document.createElement('div');
    field.className='field option-field';
    field.innerHTML=`<div class="option-label-row"><label for="opt${index+1}">Option ${index+1}</label>${index>=2?`<button class="remove-option" type="button" data-remove-option="${index}">Remove</button>`:''}</div><input id="opt${index+1}" class="option-input" maxlength="100" autocomplete="off" placeholder="Option ${index+1}" required value="${escapeHtml(value)}">`;
    $('optionFields').appendChild(field);
  });
  updateOptionControls();
  $('opt'+values.length)?.focus();
}

function removeOption(index){
  const inputs=[...document.querySelectorAll('.option-input')];
  if(inputs.length<=2){
    return;
  }
  inputs.splice(index,1);
  $('optionFields').innerHTML='';
  inputs.forEach((input,newIndex)=>{
    const field=document.createElement('div');
    field.className='field option-field';
    field.innerHTML=`<div class="option-label-row"><label for="opt${newIndex+1}">Option ${newIndex+1}</label>${newIndex>=2?`<button class="remove-option" type="button" data-remove-option="${newIndex}">Remove</button>`:''}</div><input id="opt${newIndex+1}" class="option-input" maxlength="100" autocomplete="off" placeholder="Option ${newIndex+1}" required>`;
    $('optionFields').appendChild(field);
    field.querySelector('input').value=input.value;
  });
  updateOptionControls();
}

async function loadPublic(){
  try{
    const polls=sharedCode?[await apiRequest(`${API}/public/${encodeURIComponent(sharedCode)}`)]:await apiRequest(API);
    $('polls').innerHTML=polls.map(poll=>renderPoll(poll)).join('');
    $('emptyState').hidden=polls.length>0;
    $('status').textContent=sharedCode?'Live poll · updates automatically':`${polls.length} ${polls.length===1?'poll':'polls'} · live updates`;
    if(sharedCode){
      $('createSection').hidden=true;
      $('heroSection').hidden=true;
      $('pollHeading').textContent='Vote now';
      $('pollEyebrow').textContent='SHARED POLL';
    }
  }catch(error){
    $('status').textContent=error.message;
  }
}

async function vote(pollId,optionId){
  const input=document.querySelector(`[data-voter="${pollId}"]`);
  const error=$(`error-${pollId}`);
  const name=input?.value.trim();
  if(!name){
    if(input){input.focus();}
    error.textContent='Enter your name before voting.';
    return;
  }
  const other=document.querySelector(`[data-other-input="${pollId}"]`);
  const customText=optionId===-1?other?.value.trim():null;
  if(optionId===-1&&!customText){
    error.textContent='Write your own opinion for Other.';
    if(other){other.focus();}
    return;
  }
  try{
    await apiRequest(`${API}/${pollId}/vote`,{method:'POST',headers:{'Content-Type':'application/json','X-Voter-Id':voterId},body:JSON.stringify({voterName:name,optionId,customText})});
    voterName=name;
    localStorage.setItem(NAME_KEY,name);
    showToast('Vote recorded successfully');
    await loadPublic();
  }catch(errorResponse){
    error.textContent=errorResponse.message;
  }
}

async function createPoll(event){
  event.preventDefault();
  const question=$('question').value.trim();
  const options=[...document.querySelectorAll('.option-input')].map(input=>input.value.trim());
  if(!question||options.some(option=>!option)){
    $('createError').textContent='Please complete the question and every option.';
    return;
  }
  try{
    const response=await apiRequest(API,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question,options})});
    $('createForm').reset();
    renderCreateOptions();
    showCredentials(response);
    showToast('Poll created successfully');
    await loadPublic();
  }catch(error){
    $('createError').textContent=error.message;
  }
}

function showCredentials(response){
  $('credentialsCard').hidden=false;
  $('credentialsCard').innerHTML=`<div class="credentials-icon">✓</div><div><strong>Poll created successfully</strong><p>Save these private creator credentials. You can use them with Creator login to manage only this poll.</p><div class="credential-grid"><span>Username</span><b>${escapeHtml(response.creatorUsername)}</b><span>Password</span><b>${escapeHtml(response.creatorPassword)}</b></div><div class="share-row"><span>Share link</span><button class="action-btn copy-btn" data-action="copy" data-url="${escapeHtml(`${location.origin}${response.shareUrl}`)}">Copy link</button></div></div>`;
  $('credentialsCard').scrollIntoView({behavior:'smooth',block:'center'});
}

function openLogin(kind){
  loginKind=kind;
  $('loginModal').hidden=false;
  $('loginTitle').textContent=kind==='admin'?'Admin login':'Creator login';
  $('loginCopy').textContent=kind==='admin'?'Main admin can manage every poll.':'Use the username and password shown when you created your poll.';
  $('loginUsername').value='';
  $('loginPassword').value='';
  $('loginError').textContent='';
  $('loginUsername').focus();
}

function closeLogin(){
  $('loginModal').hidden=true;
}

async function submitLogin(event){
  event.preventDefault();
  const username=$('loginUsername').value.trim();
  const password=$('loginPassword').value;
  if(!username||!password){
    $('loginError').textContent='Enter both username and password.';
    return;
  }
  try{
    const response=await apiRequest(`/api/auth/${loginKind}/login`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password})});
    if(loginKind==='admin'){
      adminToken=response.token;
      sessionStorage.setItem(ADMIN_AUTH,adminToken);
      mode='admin';
    }else{
      creatorAuth={username,token:response.token};
      sessionStorage.setItem(CREATOR_AUTH,JSON.stringify(creatorAuth));
      mode='creator';
    }
    closeLogin();
    await loadDashboard();
  }catch(error){
    $('loginError').textContent=error.message;
  }
}

async function loadDashboard(){
  if(mode==='admin'){
    $('dashboardTitle').textContent='All polls';
    $('dashboardEyebrow').textContent='MAIN ADMIN';
    $('credentialsCard').hidden=true;
    try{
      const polls=await apiRequest('/api/auth/admin/polls',{headers:{'X-Admin-Token':adminToken}});
      $('dashboardPolls').innerHTML=polls.map(poll=>renderPoll(poll,true)).join('');
      $('dashboardSection').hidden=false;
      $('pollSection').hidden=true;
      $('createSection').hidden=true;
      $('heroSection').hidden=true;
    }catch(error){
      sessionStorage.removeItem(ADMIN_AUTH);
      adminToken=null;
      mode='public';
      openLogin('admin');
    }
  }else if(mode==='creator'){
    $('dashboardTitle').textContent='My poll';
    $('dashboardEyebrow').textContent='POLL CREATOR';
    $('credentialsCard').hidden=true;
    try{
      const poll=await apiRequest('/api/auth/creator/poll',{headers:{'X-Creator-Username':creatorAuth.username,'X-Creator-Token':creatorAuth.token}});
      $('dashboardPolls').innerHTML=renderPoll(poll,true);
      $('dashboardSection').hidden=false;
      $('pollSection').hidden=true;
      $('createSection').hidden=true;
      $('heroSection').hidden=true;
    }catch(error){
      sessionStorage.removeItem(CREATOR_AUTH);
      creatorAuth=null;
      mode='public';
      openLogin('creator');
    }
  }
}

async function editPoll(id){
  try{
    const headers=managementHeaders();
    const poll=await apiRequest(`${API}/${id}`,{headers});
    const original=poll.options.filter(option=>!option.other).map(option=>option.text);
    const question=prompt('Edit question:',poll.question);
    if(question===null){return;}
    const raw=prompt('Edit options, one per line:',original.join('\n'));
    if(raw===null){return;}
    const options=raw.split('\n').map(value=>value.trim()).filter(Boolean);
    await apiRequest(`${API}/${id}`,{method:'PUT',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({question,options})});
    showToast('Poll updated successfully');
    await loadDashboard();
  }catch(error){
    showToast(error.message);
  }
}

function managementHeaders(){
  if(mode==='admin'){
    return {'X-Admin-Token':adminToken};
  }
  return {'X-Creator-Username':creatorAuth.username,'X-Creator-Token':creatorAuth.token};
}

async function closePoll(id){
  if(!confirm('Close this poll? Voting will stop immediately.')){return;}
  try{
    await apiRequest(`${API}/${id}/close`,{method:'POST',headers:{'X-Poll-Admin-Token':mode==='admin'?adminToken:creatorAuth.token}});
    showToast('Poll closed successfully');
    await loadDashboard();
  }catch(error){
    showToast(error.message);
  }
}

async function deletePoll(id){
  if(mode!=='admin'||!confirm('Delete this poll permanently?')){return;}
  try{
    await apiRequest(`${API}/${id}`,{method:'DELETE',headers:{'X-Admin-Token':adminToken}});
    showToast('Poll deleted successfully');
    await loadDashboard();
  }catch(error){
    showToast(error.message);
  }
}

function logout(){
  mode='public';
  adminToken=null;
  creatorAuth=null;
  sessionStorage.removeItem(ADMIN_AUTH);
  sessionStorage.removeItem(CREATOR_AUTH);
  $('dashboardSection').hidden=true;
  $('pollSection').hidden=false;
  $('createSection').hidden=Boolean(sharedCode);
  $('heroSection').hidden=Boolean(sharedCode);
  loadPublic();
}

$('createForm').addEventListener('submit',createPoll);
$('addOptionBtn').addEventListener('click',addOption);
$('optionFields').addEventListener('click',event=>{
  const button=event.target.closest('[data-remove-option]');
  if(button){removeOption(Number(button.dataset.removeOption));}
});
$('adminLoginBtn').addEventListener('click',()=>openLogin('admin'));
$('creatorLoginBtn').addEventListener('click',()=>openLogin('creator'));
$('modalClose').addEventListener('click',closeLogin);
$('loginModal').addEventListener('click',event=>{if(event.target===$('loginModal')){closeLogin();}});
$('loginForm').addEventListener('submit',submitLogin);
$('refreshBtn').addEventListener('click',loadPublic);
$('dashboardLogout').addEventListener('click',logout);
$('credentialsCard').addEventListener('click',event=>{
  const button=event.target.closest('[data-action="copy"]');
  if(button){navigator.clipboard?.writeText(button.dataset.url).then(()=>showToast('Poll link copied')).catch(()=>prompt('Copy this poll link:',button.dataset.url));}
});

$('polls').addEventListener('click',event=>{
  const button=event.target.closest('[data-action]');
  if(!button){return;}
  if(button.dataset.action==='vote'){vote(Number(button.dataset.poll),Number(button.dataset.option));}
  if(button.dataset.action==='copy'){navigator.clipboard?.writeText(button.dataset.url).then(()=>showToast('Poll link copied')).catch(()=>prompt('Copy this poll link:',button.dataset.url));}
});

$('dashboardPolls').addEventListener('click',event=>{
  const button=event.target.closest('[data-action]');
  if(!button){return;}
  const id=Number(button.dataset.poll);
  if(button.dataset.action==='edit'){editPoll(id);}
  if(button.dataset.action==='close'){closePoll(id);}
  if(button.dataset.action==='delete'){deletePoll(id);}
});

$('polls').addEventListener('input',event=>{
  if(event.target.classList.contains('voter-input')){
    voterName=event.target.value;
    localStorage.setItem(NAME_KEY,voterName);
  }
});

$('credentialsCard').addEventListener('click',event=>{
  const button=event.target.closest('[data-action="copy"]');
  if(button){
    navigator.clipboard?.writeText(button.dataset.url).then(()=>showToast('Poll link copied')).catch(()=>prompt('Copy this poll link:',button.dataset.url));
  }
});

renderCreateOptions();
if(sharedCode){
  $('createSection').hidden=true;
  $('heroSection').hidden=true;
}
if(adminToken){
  mode='admin';
  loadDashboard();
}else if(creatorAuth){
  mode='creator';
  loadDashboard();
}else{
  loadPublic();
}
setInterval(()=>{if(mode==='public'){loadPublic();}},5000);
