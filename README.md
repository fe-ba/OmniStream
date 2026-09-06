# Póster de Arquitectura Empresarial — Box-Man

El PDF final (`main.pdf`) lo genera GitHub automáticamente en cada push a `main`.
Cada uno solo sube `.tex` e imágenes.

## Requisitos

**Para editar:** nada especial. Cualquier editor de texto sirve.

**Para compilar en local** (recomendado):

- Una distribución de LaTeX: **TeX Live 2023+**, **MiKTeX** o **MacTeX**
- El motor **LuaLaTeX** (viene incluido en las tres)
- **latexmk** y **biber** (también vienen incluidos)

No hace falta Inkscape, ni shell-escape, ni instalar ninguna fuente.
El proyecto es autocontenido.

**Para Overleaf:** funciona tal cual. Ver la sección de Overleaf más abajo.

## Estructura

```
main.tex                  solo includes
constantes.tex            rutas de imágenes y nombres de puntos de vista
secciones/01-portada.tex  título, integrantes, misión, visión, objetivos
secciones/02..07          una capa por archivo
secciones/08-referencias.tex
imgs/NN-capa/             imágenes de cada capa
formato/                  maquetación del profesor (no tocar)
referencias/              archivos .bib
posterBoxMan.pdf          póster resumen que se incrusta como página 4
```

Son **3 planchas físicas** de 58.8 × 33 cm más el póster resumen. Las capas se
colocan en coordenadas absolutas dentro de su plancha, por eso los archivos de
`secciones/` son fragmentos sin `\begin{NuevaPagina}`: ese envoltorio vive en
`main.tex`.

| Plancha | Contiene |
|---|---|
| 1 | Portada + Motivación |
| 2 | Estrategia + Negocio |
| 3 | Aplicación + Tecnología + Proyecto + Referencias |
| 4 | `posterBoxMan.pdf` |

## Compilar en local

```bash
latexmk -pdflua main.tex
```

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

1. Exporta el diagrama desde Archi a PDF (guarda el SVG al lado como respaldo).
2. Ponlo en `imgs/NN-tucapa/`.
3. Úsalo con la macro de ruta de tu capa:

```latex
\includegraphics[scale=0.8]{\ImgTec MiDiagrama.pdf}
```

Macros disponibles en `constantes.tex`: `\ImgMot`, `\ImgEtg`, `\ImgNeg`,
`\ImgApl`, `\ImgTec`, `\ImgPry`. Ojo al espacio después de la macro: hace de
separador. Sin él LaTeX leería `\ImgTecMiDiagrama` y fallaría.

## Cambios hechos sobre la plantilla original

- `\usepackage{minted}` comentado: no se usaba y rompía la compilación.
- Paquete `svg` eliminado. El escudo ahora es `stjCartoon.pdf` y se carga con
  `\includegraphics`. Esto quita la dependencia de Inkscape y de shell-escape.
- Fuentes renombradas sin espacios (`JustaMoment.ttf`, `KaBlam.ttf`) y cargadas
  por ruta relativa con `Path=`, no por nombre de sistema. Así no hay que
  instalarlas en cada máquina.
- `\pgfmathsetseed{2024}` en `main.tex`: fija el efecto "trazo a mano" de las
  líneas separadoras. Sin esto el PDF cambiaba en cada compilación.
- Eliminados `imgs/modelo/` (sin uso), 34 macros muertas y 8 paquetes del
  preámbulo que no se usaban.
