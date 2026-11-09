"""Turns a built page body into its Markdown twin. Handles only the tags the pages use. Standard library only."""
import re
from html.parser import HTMLParser


class MarkdownTwin(HTMLParser):
    """Turns a page body into Markdown. Handles only the tags the pages use."""
    BLOCKS = {'p', 'li', 'h1', 'h2', 'h3', 'pre', 'dt', 'dd', 'figcaption'}
    VOID = {'img', 'br', 'hr', 'meta', 'source'}

    def __init__(self):
        super().__init__()
        self.out, self.line, self.href, self.skip, self.lists, self.pre = [], '', [], 0, [], False
        self.row, self.rows_done, self.in_head = None, 0, False

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag in self.VOID and (self.skip or 'data-md-skip' in attrs):
            return
        if self.skip or 'data-md-skip' in attrs:
            self.skip += 1
            return
        if tag == 'table':
            self.rows_done = 0
            self.out.append('')
        elif tag == 'thead':
            self.in_head = True
        elif tag == 'tr':
            self.row = []
        elif tag in ('td', 'th'):
            self.line = ''
        elif tag in ('ul', 'ol'):
            self.lists.append([tag, 0])
        elif tag == 'li':
            kind = self.lists[-1] if self.lists else ['ul', 0]
            kind[1] += 1
            self.line = f'{kind[1]}. ' if kind[0] == 'ol' else '- '
        elif tag in ('h1', 'h2', 'h3'):
            self.line = '#' * int(tag[1]) + ' '
        elif tag == 'pre':
            self.pre = True
            self.line = '```\n'
        elif tag == 'dt':
            self.line = '**'
        elif tag == 'a':
            self.href.append(attrs.get('href', ''))
            self.line += '['
        elif tag == 'code' and not self.pre:
            self.line += '`'
        elif tag == 'strong':
            self.line += '**'
        elif tag == 'br':
            self.line += '  \n'

    def handle_endtag(self, tag):
        if self.skip:
            self.skip -= 1
            return
        if tag in ('td', 'th') and self.row is not None:
            self.row.append(self.line.strip().replace('|', '\\|'))
            self.line = ''
            return
        if tag == 'tr' and self.row is not None:
            self.out.append('| ' + ' | '.join(self.row) + ' |')
            if self.rows_done == 0:
                self.out.append('|' + ' --- |' * len(self.row))
            self.rows_done += 1
            self.row = None
            return
        if tag == 'thead':
            self.in_head = False
            return
        if tag == 'table':
            self.out.append('')
            return
        if tag == 'a' and self.href:
            href = self.href.pop()
            if href.endswith('.html') and '://' not in href:
                href = href[:-5] + '.md'
            self.line += f']({href})'
        elif tag == 'code' and not self.pre:
            self.line += '`'
        elif tag == 'strong':
            self.line += '**'
        elif tag == 'dt':
            self.line += '**'
        elif tag in ('ul', 'ol') and self.lists:
            self.lists.pop()
            self.out.append('')
        if tag == 'pre':
            self.line = self.line.rstrip('\n') + '\n```'
            self.pre = False
        if tag in self.BLOCKS:
            self.out.append(self.line.strip(' ') if not tag == 'pre' else self.line)
            if tag not in ('li', 'dt'):
                self.out.append('')
            self.line = ''

    def handle_data(self, data):
        if self.skip:
            return
        self.line += data if self.pre else re.sub(r'\s+', ' ', data)

    def markdown(self):
        text = '\n'.join(self.out)
        return re.sub(r'\n{3,}', '\n\n', text).strip() + '\n'


def to_markdown(body):
    parser = MarkdownTwin()
    parser.feed(body)
    return parser.markdown()
