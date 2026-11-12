package com.example.model;

/**
 * Enum representativo dos Perfis de Usuário no sistema de Controle de Acesso
 * Baseado em Papéis (RBAC - Role-Based Access Control) do CuidarPlus (ILPI Vovô Lar).
 *
 * Matriz de Atribuições:
 * 1. ADMINISTRADOR: Acesso irrestrito a todas as funcionalidades do sistema, incluindo CRUD
 *    de idosos, medicamentos, usuários, auditoria, configurações e relatórios.
 * 2. ENFERMEIRO: Gestão clínica dos residentes, com poderes para cadastrar e atualizar
 *    prescrições e fichas médicas, registrar e visualizar estoque e gerar relatórios clínicos/PDF.
 * 3. CUIDADOR: Operação assistencial de linha de frente, permitindo a consulta aos residentes,
 *    confirmação da administração de doses pontuais, alerta de estoque baixo e acionamento do
 *    familiar via WhatsApp. Proibido de cadastrar, alterar ou excluir registros mestres.
 */
public enum PerfilUsuario {

    ADMINISTRADOR(
            "ADMIN",
            "Administrador",
            "Acesso total (CRUD de idosos, medicamentos, estoque, relatórios e configurações)."
    ),
    ENFERMEIRO(
            "ENFERMEIRO",
            "Enfermeiro(a)",
            "Acesso clínico (Cadastro/edição de prontuários, prescrições médicas e relatórios de estoque)."
    ),
    CUIDADOR(
            "CUIDADOR",
            "Cuidador(a)",
            "Acesso operacional (Visualização de residentes, baixa de doses, alerta de estoque e WhatsApp)."
    );

    private final String codigo;
    private final String titulo;
    private final String descricao;

    PerfilUsuario(String codigo, String titulo, String descricao) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.descricao = descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Converte um código textual (ex: "ADMIN", "ENFERMEIRO", "CUIDADOR") para a respectiva constante.
     * Retorna ADMINISTRADOR por padrão de segurança ou caso o valor seja nulo/inválido.
     */
    public static PerfilUsuario fromCodigo(String codigo) {
        if (codigo == null) return ADMINISTRADOR;
        for (PerfilUsuario p : values()) {
            if (p.codigo.equalsIgnoreCase(codigo.trim()) || p.name().equalsIgnoreCase(codigo.trim())) {
                return p;
            }
        }
        return ADMINISTRADOR;
    }

    // =========================================================================
    // Métodos de Consulta Direta à Matriz de Permissões RBAC
    // =========================================================================

    public boolean podeCadastrarIdoso() {
        return this == ADMINISTRADOR;
    }

    public boolean podeEditarIdoso() {
        return this == ADMINISTRADOR;
    }

    public boolean podeExcluirIdoso() {
        return this == ADMINISTRADOR;
    }

    public boolean podeCadastrarMedicamento() {
        return this == ADMINISTRADOR || this == ENFERMEIRO;
    }

    public boolean podeEditarMedicamento() {
        return this == ADMINISTRADOR || this == ENFERMEIRO;
    }

    public boolean podeExcluirMedicamento() {
        return this == ADMINISTRADOR || this == ENFERMEIRO;
    }

    public boolean podeGerarRelatorio() {
        return this == ADMINISTRADOR || this == ENFERMEIRO;
    }

    public boolean podeConfirmarDose() {
        return true; // Todos os perfis podem registrar aplicação de dose
    }

    public boolean podeAcessarEstoque() {
        return true; // Todos podem consultar estoque e alertar reposição
    }

    public boolean podeEnviarMensagemWhatsApp() {
        return true; // Comunicação com o familiar permitida a todos os profissionais
    }

    public boolean podeAlterarConfiguracoes() {
        return this == ADMINISTRADOR;
    }
}
