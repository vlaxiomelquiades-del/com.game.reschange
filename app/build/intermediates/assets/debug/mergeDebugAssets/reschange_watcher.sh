#!/system/bin/sh
# GameResChange watcher
#
# Roda em loop como root, detecta o app em primeiro plano via
# `dumpsys window` (mesma tecnica usada pelo PerfGame) e troca a
# RESOLUCAO REAL DA TELA com `wm size WxH` quando o app esta na lista
# configurada. Nao depende de GameManagerService, CATEGORY_GAME ou
# Game Space — `wm size` e um comando universal do WindowManager,
# funciona em qualquer ROM/Android.
#
# Formato do CONFIG (uma linha por app): com.exemplo.app=0.80

CONFIG="/data/local/tmp/reschange_config.txt"

# Descobre a resolucao fisica nativa uma unica vez ao iniciar
PHYS=$(wm size | grep -oE '[0-9]+x[0-9]+' | head -n1)
PW=$(echo "$PHYS" | cut -dx -f1)
PH=$(echo "$PHYS" | cut -dx -f2)

LAST_PKG=""

while true; do
    FOCUS=$(dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | tail -n1)
    PKG=$(echo "$FOCUS" | sed -n 's#.*[ {]\([a-zA-Z0-9_.]*\)/[^} ]*}\{0,1\}.*#\1#p')

    if [ -n "$PKG" ] && [ "$PKG" != "$LAST_PKG" ]; then
        LAST_PKG="$PKG"

        if [ -f "$CONFIG" ]; then
            SCALE=$(grep "^$PKG=" "$CONFIG" | tail -n1 | cut -d= -f2)
        else
            SCALE=""
        fi

        if [ -n "$SCALE" ]; then
            NW=$(awk -v p="$PW" -v s="$SCALE" 'BEGIN{ w=int(p*s); if (w%2!=0) w--; print w }')
            NH=$(awk -v p="$PH" -v s="$SCALE" 'BEGIN{ h=int(p*s); if (h%2!=0) h--; print h }')
            wm size "${NW}x${NH}"
        else
            wm size reset
        fi
    fi

    sleep 1
done
