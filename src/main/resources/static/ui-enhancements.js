(() => {
  const collapsedPolls = new Set();
  const pollContainers = ['polls', 'dashboardPolls'];

  function installCollapseStyles() {
    if (document.getElementById('ui-enhancement-styles')) {
      return;
    }

    const style = document.createElement('style');
    style.id = 'ui-enhancement-styles';
    style.textContent = `
      .poll.is-collapsed > :not(.poll-header) {
        display: none !important;
      }
      .poll.is-collapsed {
        padding-bottom: 18px;
      }
    `;
    document.head.appendChild(style);
  }

  async function copyText(text) {
    if (!text) {
      return false;
    }

    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch (error) {
      // Use the HTTP-compatible fallback below.
    }

    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.setAttribute('readonly', '');
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    textarea.style.top = '0';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();

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
    const button = poll.querySelector('[data-enhance="expand"]');
    if (!button) {
      return;
    }

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
      if (collapsedPolls.has(poll.dataset.pollId)) {
        setCollapsed(poll, true);
      }
      return;
    }

    const header = poll.querySelector('.poll-header');
    if (!header) {
      return;
    }

    let actions = header.querySelector('.poll-header-actions');
    if (!actions) {
      actions = document.createElement('div');
      actions.className = 'poll-header-actions';
      header.appendChild(actions);
    }

    const copyButton = header.querySelector('[data-action="copy"]');
    if (copyButton && copyButton.parentElement !== actions) {
      actions.insertBefore(copyButton, actions.firstChild);
    }

    const expandButton = document.createElement('button');
    expandButton.type = 'button';
    expandButton.className = 'action-btn expand-btn';
    expandButton.dataset.enhance = 'expand';
    expandButton.dataset.poll = poll.dataset.pollId;
    expandButton.setAttribute('aria-expanded', 'true');
    expandButton.textContent = 'Collapse';
    actions.appendChild(expandButton);

    if (collapsedPolls.has(poll.dataset.pollId)) {
      setCollapsed(poll, true);
    }
  }

  function distributePolls(container) {
    const directPolls = [...container.children].filter(child => child.classList.contains('poll'));
    if (!directPolls.length) {
      return;
    }

    const columns = document.createElement('div');
    columns.className = 'poll-masonry-columns';
    const left = document.createElement('div');
    const right = document.createElement('div');
    left.className = 'poll-column';
    right.className = 'poll-column';
    columns.append(left, right);
    container.appendChild(columns);

    directPolls.forEach(poll => {
      const target = left.offsetHeight <= right.offsetHeight ? left : right;
      target.appendChild(poll);
    });
  }

  function enhanceAll() {
    pollContainers.forEach(id => {
      const container = document.getElementById(id);
      if (!container) {
        return;
      }

      distributePolls(container);
      container.querySelectorAll('.poll').forEach(enhancePoll);
    });
  }

  document.addEventListener('click', event => {
    const copyButton = event.target.closest('[data-action="copy"]');
    if (copyButton) {
      event.preventDefault();
      event.stopImmediatePropagation();
      const url = copyButton.dataset.url || '';
      copyText(url).then(copied => {
        if (copied && typeof showToast === 'function') {
          showToast('Poll link copied');
        } else if (url) {
          window.prompt('Copy this poll link:', url);
        }
      });
      return;
    }

    const expandButton = event.target.closest('[data-enhance="expand"]');
    if (!expandButton) {
      return;
    }

    event.preventDefault();
    event.stopImmediatePropagation();
    const poll = expandButton.closest('.poll');
    if (poll) {
      setCollapsed(poll, !poll.classList.contains('is-collapsed'));
    }
  }, true);

  installCollapseStyles();
  enhanceAll();

  const observer = new MutationObserver(() => {
    window.requestAnimationFrame(enhanceAll);
  });
  observer.observe(document.body, { childList: true, subtree: true });
})();
