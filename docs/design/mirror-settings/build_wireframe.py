#!/usr/bin/env python3
"""Maintained source for the mirror-review wireframe; no App/network operations.
Run with Python stdlib. Skill owns CSS/runtime; final HTML is a derived artifact.
"""
from pathlib import Path
from html import escape
import importlib.util
import shutil

HERE = Path(__file__).resolve().parent
SKILL = Path('/var/minis/skills/easy-wireframe')
DOC_ID = 'ohmydrift-mirror-settings-review-v1'


def icon(name):
    return f'<i data-lucide="{name}" aria-hidden="true"></i>'


def nav(add=False):
    right = (f'<span class="nav-btn mirror-floating" data-prd="I-M05" aria-label="添加镜像">{icon("plus")}</span>'
             if add else '<span class="nav-btn ghost" aria-hidden="true"></span>')
    return (f'<div class="navbar mirror-nav"><span class="nav-btn mirror-floating" data-prd="I-M12" aria-label="返回">{icon("chevron-left")}</span>'
            f'<span class="title" aria-hidden="true"></span>{right}</div>')


def row(label, name, tail='', action='', secondary='', disabled=False):
    attrs = f' data-prd="{action}"' if action else ''
    attrs += ' aria-disabled="true"' if disabled else ''
    klass = 't2' if disabled else 't1'
    sub = f'<div class="t2">{escape(secondary)}</div>' if secondary else ''
    return (f'<div class="list-item"{attrs}><span class="ic">{icon(name)}</span>'
            f'<div class="txt"><div class="{klass}">{escape(label)}</div>{sub}</div>{tail}</div>')


def title(text, name='github'):
    return f'<div class="section-cap mirror-caption">{icon(name)} {escape(text)}</div>'


def mirror_item(label, host, result, order, selected=False):
    state = 'check-circle-2' if selected else 'circle'
    result_class = 'is-error' if result == '不可用' else ('is-idle' if result == '未测试' else '')
    latency = result.split()[0] if result.endswith(' ms') else ''
    return (f'<div class="list-item mirror-source" data-prd="I-M02" data-order="{order}" data-latency="{latency}" '
            f'aria-label="{escape(label)}{", 当前选择" if selected else ""}">'
            f'<span class="chev">{icon(state)}</span><div class="txt"><div class="t1">{escape(label)}</div>'
            f'<div class="t2">{escape(host)}</div></div><span class="mirror-result {result_class}">{escape(result)}</span>'
            f'<span class="chev mirror-more" data-prd="I-M06" aria-label="管理{escape(label)}">{icon("more-horizontal")}</span></div>')


def main(testing=False):
    switch = '<div class="switch on" aria-label="启用镜像，已开启"><div class="knob"></div></div>'
    settings = '<div class="card">' + row('启用镜像', 'power', switch, 'I-M01') + '</div>'
    sort = ('<button type="button" class="mirror-sort" data-prd="I-M03" data-sort-mode="manual" '
            'aria-label="排序：手动顺序；点击切换为耗时低到高" title="手动顺序">'
            + icon('arrow-up-down') + '</button>')
    items = [mirror_item('直连', 'github.com', '342 ms', 0),
             mirror_item('镜像 A', 'mirror-a.example', '186 ms', 1, True),
             mirror_item('镜像 B', 'mirror-b.example', '未测试', 2),
             mirror_item('镜像 C', 'mirror-c.example', '不可用', 3)]
    card = ('<div class="card mirror-sources"><div class="card-hd"><span class="ct">' + icon('list')
            + ' 镜像源</span>' + sort + '</div><div class="mirror-rows">' + ''.join(items) + '</div></div>')
    button = (icon('stop-circle') + ' 停止测试' if testing else icon('play') + ' 测试全部')
    return (nav(True) + '<div class="body">' + title('GitHub 镜像') + settings + card + '</div>'
            + f'<div class="cta-bar"><div class="btn {"ghost" if testing else "primary"}" data-prd="I-M04">{button}</div></div>')


def field(label, name, value='', preview=False, action='I-M07'):
    if preview:
        value = '<wbr>'.join(escape(value[i:i + 12]) for i in range(0, len(value), 12))
    else:
        value = escape(value)
    klass = 'finput mirror-preview' if preview else 'finput mirror-input'
    return (f'<div class="form-field" data-prd="{action}"><div class="flabel">{icon(name)} {label}</div>'
            f'<div class="{klass}"><span>{value}</span></div></div>')


def notice(text, retry=False):
    button = '<span class="pbtn" data-prd="I-M08">重试</span>' if retry else ''
    return (f'<div class="perm-tip mirror-error" data-prd="I-M08"><span class="pic">{icon("circle-alert")}</span>'
            f'<div class="ptxt"><div class="pt1">{text}</div></div>{button}</div>')


def modal(label, content, prd, heading_icon='github'):
    header = (f'<div class="mirror-modal-head"><span>{icon(heading_icon)} {label}</span>'
              f'<span class="mirror-close" data-prd="I-M12" aria-label="关闭{label}">{icon("x")}</span></div>')
    return ('<div class="mirror-background" data-export-blur="4" aria-hidden="true" inert>' + main() + '</div>'
            f'<div class="mirror-overlay"><section class="mirror-dialog" role="dialog" aria-modal="true" '
            f'aria-label="{label}" data-prd="{prd}" data-material="settings">{header}'
            f'<div class="mirror-modal-body">{content}</div></section></div>')


def editor(kind='edit', dropdown=False):
    empty, invalid = kind == 'add', kind == 'invalid'
    label = '添加镜像' if empty else '编辑镜像'
    value = '' if empty else ('http://mirror-a.example/' if invalid else 'https://mirror-a.example/')
    mode = '<span class="cta-hint">HTTPS 前缀</span><span class="chev">' + icon('arrow-up-down') + '</span>'
    fields = field('名称', 'type', '' if empty else '镜像 A')
    fields += '<div class="mirror-mode-wrap">' + row('拼接方式', 'link', mode, 'I-M09')
    if dropdown:
        fields += ('<div class="mirror-dropdown" role="listbox" aria-label="拼接方式" data-material="settings">'
                   '<div role="option" aria-selected="true" data-prd="I-M09">HTTPS 前缀'
                   f'<span>{icon("check")}</span></div></div>')
    fields += '</div>' + field('地址', 'globe', value)
    if invalid:
        fields += notice('仅支持 HTTPS 地址')
    else:
        preview = ('' if empty else 'https://mirror-a.example/https://raw.githubusercontent.com/github/gitignore/29d931584421f77a7d0bdaa43b0b75be50fae435/Kotlin.gitignore')
        fields += field('改写预览', 'external-link', preview, True, 'I-M08')
    if kind == 'failed':
        fields += notice('未能保存，草稿已保留', True)
    return modal(label, fields, 'I-M07')


def manager():
    actions = row('测试', 'play', action='I-M04')
    actions += row('编辑', 'pencil', action='I-M07')
    actions += row('上移', 'arrow-up', action='I-M10', disabled=True)
    actions += row('下移', 'arrow-down', action='I-M10')
    actions += '<div class="mirror-danger-row">' + row('删除', 'trash-2', action='I-M11') + '</div>'
    return modal('镜像 A', actions, 'I-M06')


def mode_menu():
    return editor(dropdown=True)


def deletion():
    content = ('<div class="mirror-confirm-text">删除后当前选择切换为直连。</div>'
               '<div class="mirror-confirm-actions"><div class="btn ghost" data-prd="I-M11">'
               f'{icon("shield-check")}保留</div><div class="btn mirror-delete" data-prd="I-M11">'
               f'{icon("trash-2")}删除</div></div>')
    return modal('删除镜像 A？', content, 'I-M11', 'trash-2')


def build():
    screens = [
        ('P-M01 · 镜像列表', 'mirror-main', '1,1', main()),
        ('P-M02 · 条目管理', 'mirror-manage', '1,2', manager()),
        ('P-M03 · 编辑镜像弹窗', 'mirror-edit', '1,3', editor()),
        ('P-M04 · 添加镜像弹窗', 'mirror-add', '2,1', editor('add')),
        ('P-M05 · 拼接下拉框', 'mirror-mode', '2,2', mode_menu()),
        ('P-M06 · 地址校验失败', 'mirror-invalid', '2,3', editor('invalid')),
        ('P-M07 · 测试与停止', 'mirror-testing', '3,1', main(True)),
        ('P-M08 · 保存失败／保稿', 'mirror-write-failed', '3,2', editor('failed')),
        ('P-M09 · 删除当前镜像', 'mirror-delete', '3,3', deletion()),
    ]
    links = [
        ('mirror-main', 'mirror-manage', '点 ⋯', False),
        ('mirror-manage', 'mirror-edit', '点编辑', False),
        ('mirror-main', 'mirror-add', '点 ＋', False),
        ('mirror-add', 'mirror-mode', '点拼接方式', False),
        ('mirror-add', 'mirror-invalid', '完成无效地址', True),
        ('mirror-main', 'mirror-testing', '点测试全部', False),
        ('mirror-edit', 'mirror-write-failed', '保存失败', True),
        ('mirror-manage', 'mirror-delete', '点删除', True),
        ('mirror-edit', 'mirror-mode', '点拼接方式', True),
        ('mirror-invalid', 'mirror-edit', '修正并完成', True),
        ('mirror-write-failed', 'mirror-edit', '重试成功', True),
    ]
    markup = '\n'.join(
        f'<div class="stage" data-cell="{cell}"><div class="stage-label">{escape(label)}</div>'
        f'<div class="screen mirror-wireframe" id="{screen_id}" data-prd="{label.split(" · ")[0]}">{body}</div></div>'
        for label, screen_id, cell, body in screens)
    markup += '\n' + '\n'.join(
        f'<div class="link" data-from="{src}" data-to="{dest}" data-tag="{escape(tag)}"'
        f' data-side="auto" data-dash="{int(dashed)}"></div>' for src, dest, tag, dashed in links)
    spec = importlib.util.spec_from_file_location('easy_wireframe_build', SKILL / 'scripts/build.py')
    engine = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(engine)
    html = engine.bundle(markup, '镜像页 R2 · 示例数据／移动线框', DOC_ID, 'mobile')
    # Local, trusted extensions are composed by this generator, never hand-edited in the artifact.
    css = (HERE / 'mirror.css').read_text(encoding='utf-8')
    script = (HERE / 'review-interactions.js').read_text(encoding='utf-8')
    html = html.replace('</head>', '<style>' + css + '</style></head>', 1)
    html = html.replace('</body>', '<script>' + script + '</script></body>', 1)
    (HERE / 'index.html').write_text(html, encoding='utf-8')
    print(f'Created mobile R2: 9 screens, 11 links ({len(html.encode())} bytes)')
    (HERE / 'licenses').mkdir(exist_ok=True)
    for license_name in ('lucide-LICENSE', 'html2canvas-LICENSE'):
        shutil.copyfile(SKILL / 'assets/vendor' / license_name, HERE / 'licenses' / license_name)


if __name__ == '__main__':
    build()
