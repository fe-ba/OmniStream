$pdf_mode  = 4;
$pdflatex  = 'lualatex -file-line-error -interaction=nonstopmode %O %S';
$lualatex  = 'lualatex -file-line-error -interaction=nonstopmode %O %S';
$bibtex_use = 2;
@default_files = ('main.tex');
push @generated_exts, 'bcf', 'run.xml';

# ---------------------------------------------------------------------
# Diagramas: SVG -> PDF
#
# Los diagramas se exportan desde Archi como .svg y es lo unico que se
# versiona. LuaLaTeX no sabe leer SVG, asi que antes de cada compilacion
# se convierten a PDF vectorial dentro de build/svg/, respetando la
# estructura de carpetas de imgs/. main.tex apunta ahi con \graphicspath.
#
# Solo se reconvierte lo que cambio, asi que a partir de la segunda
# compilacion esto no cuesta nada. Sirve cualquiera de los tres
# conversores; se usa el primero que este instalado.
# ---------------------------------------------------------------------
use File::Find ();
use File::Basename ();
use File::Path ();

sub convertir_svg {
    return unless -d 'imgs';

    my @svg;
    File::Find::find(
        sub { push @svg, $File::Find::name if -f $_ && /\.svg$/i },
        'imgs'
    );
    return unless @svg;

    my $conversor;
    if    (!system 'command -v rsvg-convert >/dev/null 2>&1') { $conversor = 'rsvg' }
    elsif (!system 'command -v inkscape     >/dev/null 2>&1') { $conversor = 'inkscape' }
    elsif (!system 'command -v cairosvg     >/dev/null 2>&1') { $conversor = 'cairosvg' }
    else {
        warn "\n"
           . "!! No hay conversor de SVG instalado, los diagramas saldran en blanco.\n"
           . "!! Instala uno:  sudo pacman -S librsvg      (Arch)\n"
           . "!!                sudo apt install librsvg2-bin  (Debian/Ubuntu)\n"
           . "!!                sudo pacman -S inkscape / apt install inkscape\n\n";
        return;
    }

    my $hechos = 0;
    for my $svg (@svg) {
        (my $pdf = "build/svg/$svg") =~ s/\.svg$/.pdf/i;
        next if -e $pdf && (stat $pdf)[9] >= (stat $svg)[9];

        File::Path::make_path(File::Basename::dirname($pdf));

        my $cmd = $conversor eq 'rsvg'     ? "rsvg-convert -f pdf -o '$pdf' '$svg'"
                : $conversor eq 'inkscape' ? "inkscape --export-type=pdf --export-filename='$pdf' '$svg'"
                :                            "cairosvg '$svg' -o '$pdf'";

        if (system("$cmd >/dev/null 2>&1")) {
            warn "!! No se pudo convertir $svg\n";
        } else {
            $hechos++;
        }
    }
    print "Diagramas convertidos a PDF: $hechos de " . scalar(@svg) . "\n" if $hechos;
}

convertir_svg();
