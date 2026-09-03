// AIDL usado pelo UserService do Shizuku. Roda em processo separado,
// com UID de shell (2000) ou root (0) dependendo de como o Shizuku foi
// iniciado -- nunca com a identidade normal do app.
package com.game.reschange;

interface IShellService {
    String exec(String command) = 1;

    // Codigo de transacao fixo que o Shizuku usa para encerrar o
    // servico ao trocar de versao (ver ShellUserService.destroy()).
    void destroy() = 16777114;
}
