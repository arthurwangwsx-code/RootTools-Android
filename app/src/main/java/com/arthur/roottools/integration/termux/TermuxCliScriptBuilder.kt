package com.arthur.roottools.integration.termux

/**
 * Builds the Termux-side RootTools CLI.
 *
 * The token is written only into the provisioned file. It is never embedded in a command that the
 * user must paste into the interactive shell, which keeps it out of normal shell history.
 */
object TermuxCliScriptBuilder {
    fun build(token: String): String {
        require(TOKEN_REGEX.matches(token)) { "Invalid automation token" }
        return """
            #!/data/data/com.termux/files/usr/bin/sh
            set -u

            ROOTTOOLS_TOKEN='$token'
            ROOTTOOLS_COMPONENT='com.arthur.roottools/.automation.ActionRouterReceiver'
            ROOTTOOLS_ACTION='com.arthur.roottools.ACTION'

            usage() {
              cat <<'ROOTTOOLS_USAGE'
            RootTools CLI

              roottools status
              roottools mode auto|cool|performance
              roottools adb root on
              roottools adb wireless on|off
              roottools diagnose
              roottools app freeze <package>
              roottools app enable <package>
              roottools version
            ROOTTOOLS_USAGE
            }

            result_json() {
              # `am broadcast -W` prints the ordered broadcast result. RootTools uses JSON as
              # resultData; Android escapes inner quotes when rendering it in the shell.
              printf '%s\n' "${'$'}1" \
                | sed -n 's/^Broadcast completed: result=[^,]*, data="\(.*\)"${'$'}/\1/p' \
                | sed 's/\\"/"/g; s/\\\\/\\/g'
            }

            call_roottools() {
              request_id="termux-${'$'}(date +%s)-${'$'}${'$'}"
              output="${'$'}(am broadcast -W \
                -n "${'$'}ROOTTOOLS_COMPONENT" \
                -a "${'$'}ROOTTOOLS_ACTION" \
                --es token "${'$'}ROOTTOOLS_TOKEN" \
                --es request_id "${'$'}request_id" \
                "${'$'}@" 2>&1)"
              json="${'$'}(result_json "${'$'}output")"
              if [ -n "${'$'}json" ]; then
                printf '%s\n' "${'$'}json"
              else
                printf '%s\n' "${'$'}output" >&2
                return 1
              fi
            }

            bool_arg() {
              case "${'$'}1" in
                on|true|1) printf 'true' ;;
                off|false|0) printf 'false' ;;
                *) return 1 ;;
              esac
            }

            command="${'$'}{1:-help}"
            case "${'$'}command" in
              status)
                call_roottools --es command GET_STATUS
                ;;
              mode)
                mode="${'$'}{2:-}"
                case "${'$'}mode" in
                  auto|cool|performance)
                    call_roottools --es command SET_MODE --es mode "${'$'}mode"
                    ;;
                  *) usage >&2; exit 2 ;;
                esac
                ;;
              adb)
                transport="${'$'}{2:-}"
                requested="${'$'}{3:-}"
                case "${'$'}transport" in
                  root)
                    [ "${'$'}requested" = 'on' ] || { printf '%s\n' 'Root TCP ADB can only be enabled remotely.' >&2; exit 2; }
                    call_roottools --es command SET_ADB --ez enabled true
                    ;;
                  wireless)
                    enabled="${'$'}(bool_arg "${'$'}requested")" || { usage >&2; exit 2; }
                    call_roottools --es command SET_NATIVE_ADB --ez enabled "${'$'}enabled"
                    ;;
                  *) usage >&2; exit 2 ;;
                esac
                ;;
              diagnose)
                call_roottools --es command RUN_DIAGNOSTIC
                ;;
              app)
                operation="${'$'}{2:-}"
                package_name="${'$'}{3:-}"
                [ -n "${'$'}package_name" ] || { usage >&2; exit 2; }
                case "${'$'}operation" in
                  freeze) call_roottools --es command FREEZE --es package "${'$'}package_name" ;;
                  enable) call_roottools --es command UNFREEZE --es package "${'$'}package_name" ;;
                  *) usage >&2; exit 2 ;;
                esac
                ;;
              version)
                printf '%s\n' 'roottools-cli/$VERSION'
                ;;
              help|-h|--help)
                usage
                ;;
              *)
                usage >&2
                exit 2
                ;;
            esac
        """.trimIndent() + "\n"
    }

    const val VERSION = 1
    private val TOKEN_REGEX = Regex("^[A-Za-z0-9_-]{48,128}$")
}

