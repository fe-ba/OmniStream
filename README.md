# Póster de Arquitectura Empresarial — OmniStream

El PDF final (`main.pdf`) lo genera GitHub automáticamente en cada push a `main`.
Cada uno solo sube `.tex` e imágenes.

## Requisitos

**Para editar:** nada especial. Cualquier editor de texto sirve.

**Para compilar en local** (recomendado):

- Una distribución de LaTeX: **TeX Live 2023+**, **MiKTeX** o **MacTeX**
- El motor **LuaLaTeX** (viene incluido en las tres)
- **latexmk** y **biber** (también vienen incluidos)
- **rsvg-convert** (`sudo pacman -S librsvg` / `sudo apt install librsvg2-bin`):
  los diagramas se versionan como `.svg` y LuaLaTeX no sabe leerlos, así que
  `.latexmkrc` los pasa a PDF vectorial antes de compilar. Si prefieres,
  también sirve `inkscape` o `cairosvg`: se usa el primero que encuentre.

No hace falta instalar ninguna fuente: Montserrat y Lato vienen con TeX Live.

**Para Overleaf:** funciona tal cual. Ver la sección de Overleaf más abajo.

## Estructura

```
main.tex                  preámbulo, maquetación y orden de las planchas
secciones/01-portada.tex  título, integrantes, misión, visión, objetivos
secciones/02..07          una capa por archivo
secciones/08-referencias.tex
imgs/NN-capa/             diagramas .svg de cada capa
imgs/stjCartoon.svg       escudo de la cabecera
referencias/              archivos .bib
build/                    todo lo generado (PDF, auxiliares, diagramas
                          convertidos en build/svg/). No se versiona.
```

Son **3 planchas físicas** de 58.8 × 33 cm. Las capas se
colocan en coordenadas absolutas dentro de su plancha, por eso los archivos de
`secciones/` son fragmentos sin `\begin{NuevaPagina}`: ese envoltorio vive en
`main.tex`.

| Plancha | Contiene |
|---|---|
| 1 | Portada + Motivación |
| 2 | Estrategia + Negocio |
| 3 | Aplicación + Tecnología + Proyecto + Referencias |

## Compilar en local

```bash
latexmk -pdflua main.tex
```

Hay que compilar **con latexmk**: es quien convierte los `.svg` a PDF. Si
llamas a `lualatex` a secas, los diagramas saldrán en blanco.

En TeXstudio: Opciones → Configurar → Compilar → Compilador por defecto: **LuaLaTeX**.
En VS Code con LaTeX Workshop: el `.latexmkrc` del repo ya lo configura solo.

## Overleaf

1. New Project → Import from GitHub → eliges el repo.
2. Menu → Compiler: **LuaLaTeX**. Main document: **main.tex**.
3. Para bajar cambios de otros: Menu → GitHub → Pull.
   Para subir: Menu → GitHub → Push.

Overleaf no ejecuta el hook local, así que la validación llegará desde
GitHub Actions. Es lo normal.

## Flujo diario

```bash
git pull                  # SIEMPRE antes de empezar
git add .
git commit -m "..."       # el hook compila y frena si hay error
git pull                  # por si alguien subió mientras otro trabajaba
git push
```

Si al hacer pull hay conflicto en `main.pdf`, no resolver a mano, usar:

```bash
git checkout --ours main.pdf && git add main.pdf
```

## Activar el hook (una vez, solo si se compila en local)

```bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit    # solo Linux/macOS
```

## Agregar una imagen

1. Exporta el diagrama desde Archi a **SVG**.
2. Ponlo en `imgs/NN-tucapa/`.
3. Úsalo con `\Diagrama` y la ruta **sin extensión**:

```latex
\Diagrama{imgs/06-tecnologia/MiDiagrama}
```

`\Diagrama` escala la figura al máximo que quepa en su celda sin deformarla,
así que no hay que calibrar ningún `scale=` a mano ni se sale del recuadro si
alguien vuelve a exportar el `.svg` con otro tamaño. Para que se vea más
grande, dale más filas o columnas al `NuevoParrafo` que la contiene.

## Cambios hechos sobre la plantilla original

- `\usepackage{minted}` comentado: no se usaba y rompía la compilación.
- `\pgfmathsetseed{2024}` en `main.tex`: fija el efecto "trazo a mano" de las
  líneas separadoras. Sin esto el PDF cambiaba en cada compilación.
- Eliminados `imgs/modelo/` (sin uso), 34 macros muertas y 8 paquetes del
  preámbulo que no se usaban.
- Todas las figuras pasaron de `.pdf` a `.svg`. Los `.pdf` de `imgs/` se
  borraron; `.latexmkrc` regenera los que LaTeX necesita en `build/svg/`.
- Los `scale=` de cada figura se cambiaron por `\Diagrama`, que ajusta sola la
  imagen a su celda. Antes varias se salían del recuadro y pisaban la de al
  lado.
- `constantes.tex` y la carpeta `formato/` eliminadas. Las rutas y los nombres
  de los puntos de vista quedaron escritos directamente en cada sección, y la
  maquetación que seguía viva (grilla, `Marco`, `Linea`, cabeceras) está ahora
  en el preámbulo de `main.tex`.
- Fuentes `JustaMoment.ttf` / `KaBlam.ttf` y `fontspec` eliminados: el texto del
  póster es solo el de las cabeceras, y los diagramas traen su propia
  tipografía desde Archi. El documento usa Latin Modern.
- Eliminada la maquinaria de modos (`ModoE` / `ModoC`, `\Configuracion`): solo
  se usaba `ModoE`, así que sus colores quedaron fijos en el preámbulo.
- Tipografía: **Montserrat** en títulos y **Lato** en texto corrido, ambas de
  TeX Live.
- En `08-referencias.tex` se quitó el `thebibliography` hecho a mano: generaba
  un segundo título "Referencias" que el `\vspace{-30pt}` tapaba a medias.
  Ahora es una lista normal y `\printbibliography` va con `heading=none`.
