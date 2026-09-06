$pdf_mode  = 4;
$lualatex  = 'lualatex -file-line-error -interaction=nonstopmode %O %S';
$bibtex_use = 2;
@default_files = ('main.tex');
push @generated_exts, 'bcf', 'run.xml';
