export function knowledgeFileError(file?: Pick<File, 'name' | 'size'>): string {
  if (!file) return '';
  if (!/\.(md|txt|pdf)$/i.test(file.name)) return '请选择 .md、.txt 或文字版 .pdf 文件。';
  if (!file.size) return '文件为空，请选择有正文的资料。';
  const pdf = /\.pdf$/i.test(file.name);
  if (file.size > (pdf ? 5 * 1024 * 1024 : 128 * 1024)) return pdf ? 'PDF 最大 5MB，请压缩或拆分后上传。' : 'Markdown/TXT 最大 128KB，请拆分后上传。';
  return '';
}
