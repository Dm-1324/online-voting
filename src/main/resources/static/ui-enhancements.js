(() => {
  const collapsedPolls = new Set();
  const pollContainers = ['polls', 'dashboardPolls'];

  async function copyText(text) {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch (error) {
      // Fall through to the legacy copy method.
    }

    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    textarea.setSelectionRange(0, textarea.value.length);
    let copied = false;
    try {
      copied = document.execCommand('copy');
    } catch (error) {
      copied = false;
    }
    textarea.remove();
    return copied;
  }

  function setCollapsed(poll, collapsed) {
    const pollId = poll.dataset.pollId;
    const content = poll.querySelector('.poll-content');
    const button = poll.querySelector('[data-enhance="expand"]');
    if (!content || !button) {
      return;
    }
    content.hidden = collapsed;
    poll.classList.toggle('is-collapsed', collapsed);
    button.textContent = collapsed ? 'Expand' : 'Collapse';
    button.setAttribute('aria-expanded', String(!collapsed));
    if (collapsed) {
      collapsedPolls.add(pollId);
    } else {
      collapsedPolls.delete(pollId);
    }
  }

  function enhancePoll(poll) {
    if (poll.querySelector('[data-enhance="expand"]')) {
      const existingContent = poll.querySelector('.poll-content');
      if (existingContent && collapsedPolls.has(poll.dataset.pollId)) {
        setCollapsed(poll, true);
      }
      return;
    }

    const header = poll.querySelector('.poll-header');
    if (!header) {
      return;
    }

    const content = document.createElement('div');
    content.className = 'poll-content';
    const movable = [...poll.children].filter(child => child !== header);
    movable.forEach(child => content.appendChild(child));
    poll.appendChild(content);

    const actions = document.createElement('div');
    actions.className = 'poll-header-actions';
    const copyButton = header.querySelector('[data-action="copy"]');
    if (copyButton) {
      actions.appendChild(copyButton);
    }

    const expandButton = document.createElement('button');
    expandButton.type = 'button';
    expandButton.className = 'action-btn expand-btn';
    expandButton.dataset.enhance = 'expand';
    expandButton.dataset.poll = poll.dataset.pollId;
    expandButton.setAttribute('aria-expanded', 'true');
    expandButton.textContent = 'Collapse';
    actions.appendChild(expandButton);
    header.appendChild(actions);

    if (collapsedPolls.has(poll.dataset.pollId)) {
      setCollapsed(poll, true);
    }
  }

  function enhanceAll() {
    pollContainers.forEach(id => {
      const container = document.getElementById(id);
      if (!container) {
        return;
      }
      container.querySelectorAll('.poll').forEach(enhancePoll);
    });
  }

  document.addEventListener('click', event => {
    const copyButton = event.target.closest('[data-action="copy"]');
    if (copyButton) {
      event.preventDefault();
      event.stopImmediatePropagation();
      copyText(copyButton.dataset.url || '').then(copied => {
        if (copied && typeof showToast === 'function') {
          showToast('Poll link copied');
        } else if (copyButton.dataset.url) {
          window.prompt('Copy this poll link:', copyButton.dataset.url);
        }
      });
      return;
    }

    const expandButton = event.target.closest('[data-enhance="expand"]');
    if (expandButton) {
      event.preventDefault();
      event.stopPropagation();
      const poll = expandButton.closest('.poll');
      if (poll) {
        setCollapsed(poll, !poll.classList.contains('is-collapsed'));
      }
    }
  }, true);

  const observer = new MutationObserver(enhanceAll);
  observer.observe(document.body, { childList: true, subtree: true });
  enhanceAll();
})();
