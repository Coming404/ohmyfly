/* Local-only sorting illustration; never writes App settings or sends requests. */
(() => {
  const modes = [
    {key:'manual', label:'手动顺序', icon:'arrow-up-down'},
    {key:'ascending', label:'耗时低到高', icon:'arrow-up'},
    {key:'descending', label:'耗时高到低', icon:'arrow-down'}
  ];
  document.querySelectorAll('.mirror-sort').forEach(button => {
    button.addEventListener('click', event => {
      // Review pinning and moving take precedence over this cosmetic preview.
      if (document.querySelector('#annoToggle').getAttribute('aria-pressed') === 'true' ||
          document.querySelector('#layoutToggle').getAttribute('aria-pressed') === 'true') return;
      if (button.closest('[inert]')) return;
      event.stopPropagation();
      const index = (modes.findIndex(m => m.key === button.dataset.sortMode) + 1) % modes.length;
      const mode = modes[index];
      button.dataset.sortMode = mode.key;
      button.title = mode.label;
      button.setAttribute('aria-label', `排序：${mode.label}；点击切换为${modes[(index + 1) % modes.length].label}`);
      const image = document.createElement('i');
      image.dataset.lucide = mode.icon;
      image.setAttribute('aria-hidden', 'true');
      button.replaceChildren(image);
      lucide.createIcons();
      const container = button.closest('.mirror-sources').querySelector('.mirror-rows');
      const rows = [...container.children];
      rows.sort((a, b) => {
        if (!index) return Number(a.dataset.order) - Number(b.dataset.order);
        const av = a.dataset.latency, bv = b.dataset.latency;
        if (!av || !bv) return av ? -1 : bv ? 1 : Number(a.dataset.order) - Number(b.dataset.order);
        return (Number(av) - Number(bv)) * (index === 1 ? 1 : -1);
      });
      rows.forEach(row => container.append(row));
    });
  });
  document.querySelector('#docSubtitle').textContent = 'R2 · 已处理20条批注；旧批注坐标基于旧稿。排序箭头可本地预览，其余控件以流程线框表达。';
})();
