#!/bin/sh

# Modify the next line to change the JVM
JAVA_CMD=/mnt/emus/bittboy-j2me/jvm/bin/cvm

if [ -z "$MIDPATH_HOME" ]; then
  MIDPATH_HOME=$(pwd)/..
fi

if [ $# -lt 2 ]; then
 echo "Usage :"
 echo "  $(basename $0) <classpath> <midlet-class> [midlet-name]" 
 echo "  $(basename $0) -jar <jar-file>"
 exit 1
fi

# Guardar a identificação antes de qualquer shift nos argumentos.
if [ "$1" = "-jar" ]; then
  LOG_TIPO=JAR
  LOG_ARQUIVO=$2
  LOG_CLASSE=
else
  LOG_TIPO=classe
  LOG_ARQUIVO=$1
  LOG_CLASSE=$2
fi

# Set the classpath
CP=$MIDPATH_HOME/dist/midpath.jar:$MIDPATH_HOME/configuration:$MIDPATH_HOME/dist/kxml2-2.3.0.jar:$MIDPATH_HOME/dist/microbackend.jar:$MIDPATH_HOME/dist/jlayerme-cldc.jar:$MIDPATH_HOME/dist/jorbis-cldc.jar:$MIDPATH_HOME/dist/escher-cldc.jar:$MIDPATH_HOME/dist/sdljava-cldc.jar:$MIDPATH_HOME/dist/avetanabt-cldc.jar:$MIDPATH_HOME/dist/jsr172-jaxp.jar:$MIDPATH_HOME/dist/jsr172-jaxrpc.jar:$MIDPATH_HOME/dist/jsr179-location.jar:$MIDPATH_HOME/dist/jsr184-m3g.jar:$MIDPATH_HOME/dist/jsr205-messaging.jar:$MIDPATH_HOME/dist/jsr226-svg-core.jar:$MIDPATH_HOME/dist/jsr226-svg-midp2.jar:$MIDPATH_HOME/dist/jsr239-opengles-core.jar:$MIDPATH_HOME/dist/jsr239-opengles-jgl.jar:$MIDPATH_HOME/dist/jsr239-opengles-nio.jar:$MIDPATH_HOME/dist/jgl-cldc.jar:$MIDPATH_HOME/dist/nokia.jar:$MIDPATH_HOME/dist/cldc1.1.jar


# Perfil .res tem prioridade; depois procura resolução no nome do JAR.
WIDTH=176
HEIGHT=221
resolution=
if [ "$1" = "-jar" ]; then
  CP="$CP:$2"
  if [ -f "$2.res" ]; then
    IFS= read -r resolution < "$2.res" || :
  else
    filename=${2##*/}
    case "$filename" in
      *96[xX]65*) resolution=96x65 ;;
      *128[xX]128*) resolution=128x128 ;;
      *128[xX]160*) resolution=128x160 ;;
      *176[xX]208*) resolution=176x208 ;;
      *176[xX]220*) resolution=176x220 ;;
      *176[xX]221*) resolution=176x221 ;;
      *208[xX]208*) resolution=208x208 ;;
      *220[xX]176*) resolution=220x176 ;;
      *240[xX]240*) resolution=240x240 ;;
      *240[xX]320*) resolution=240x320 ;;
      *320[xX]240*) resolution=320x240 ;;
    esac
    if [ -z "$resolution" ]; then
      case "$2" in
        *96[xX]65/*|*Nokia_3410_Games/*|*_Nokia_6010_java.jar) resolution=96x65 ;;
      esac
    fi
  fi
else
  CP="$CP:$1"
  shift
fi
case "$resolution" in
  96x65|128x128|128x160|176x208|176x220|176x221|208x208|220x176|240x240|240x320|320x240)
    WIDTH=${resolution%x*}; HEIGHT=${resolution#*x} ;;
  '') ;;
  *) echo "Perfil de resolução inválido; usando 176x221" >&2 ;;
esac
# Todos os jogos no perfil 96x65 usam 2/8/4/6, salvo [setas].
DPAD=setas
LOGKEYS=0
if [ "$1" = "-jar" ] && [ "$WIDTH" = 96 ] && [ "$HEIGHT" = 65 ]; then
  DPAD=numerico
  case "${2##*/}" in *'[setas]'*|*'[SETAS]'*) DPAD=setas ;; esac
fi
case "$2" in *'[log]'*|*'[LOG]'*) LOGKEYS=1 ;; esac
MAXFPS=0
if [ "$LOG_TIPO" = "JAR" ]; then
  case "${LOG_ARQUIVO##*/}" in *15\ [fF][pP][sS]*) MAXFPS=15 ;; esac
fi
JLP=$MIDPATH_HOME/dist
CLASS=org.thenesis.midpath.main.MIDletLauncherSE
# Identificar cada execução nos dois arquivos, preservando registros anteriores.
log_header() {
  printf '\n%s\n' '=== Identificação da execução J2ME ==='
  printf 'Tipo: %s\n' "$LOG_TIPO"
  printf 'Arquivo/caminho: %s\n' "$LOG_ARQUIVO"
  if [ -n "$LOG_CLASSE" ]; then
    printf 'Classe MIDlet: %s\n' "$LOG_CLASSE"
  fi
  printf 'Resolução selecionada: %sx%s\n' "$WIDTH" "$HEIGHT"
  if [ "$MAXFPS" = 15 ]; then printf 'Limite de apresentação: 15 FPS\n'; fi
  printf '%s\n\n' '=== Registro da execução ==='
}
# Preserve cada argumento, inclusive caminhos com espaços.
{
  log_header
  log_header >&2
  exec "$JAVA_CMD" "-Dsun.boot.library.path=$JLP" "-Dj2me.direcional=$DPAD" "-Dj2me.logkeys=$LOGKEYS" "-Dj2me.maxfps=$MAXFPS" \
  "-Djavax.microedition.lcdui.screenWidth=$WIDTH" \
  "-Djavax.microedition.lcdui.screenHeight=$HEIGHT" \
  "-Xbootclasspath/p:$CP" -Xmx10M "$CLASS" "$@"
} >> j2me_std_log.txt 2>> j2me_err_log.txt
