package com.example.util;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.R;
import com.example.model.PerfilUsuario;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Utilitário central de gerenciamento de papéis e controle de permissões de acesso (RBAC) do CuidarPlus.
 * Mantém interoperabilidade total com SessaoManager e PermissaoUtils.
 */
public class PerfilManager {

    public static final String PREFS_NAME = SessaoManager.PREFS_NAME;
    public static final String KEY_PERFIL = SessaoManager.KEY_PERFIL;

    public static final String PERFIL_ADMIN = "ADMIN";
    public static final String PERFIL_ENFERMEIRO = "ENFERMEIRO";
    public static final String PERFIL_CUIDADOR = "CUIDADOR";

    /**
     * Retorna o perfil ativo nas SharedPreferences. Padrão: ADMIN.
     */
    public static String getPerfilAtual(Context context) {
        if (context == null) return PERFIL_ADMIN;
        return SessaoManager.getInstance(context).getPerfilAtivo().getCodigo();
    }

    /**
     * Define o novo perfil ativo do usuário.
     */
    public static void setPerfil(Context context, String novoPerfil) {
        if (context == null) return;
        PerfilUsuario perfil = PerfilUsuario.fromCodigo(novoPerfil);
        SessaoManager.getInstance(context).salvarPerfilAtivo(perfil);
    }

    /**
     * Regra de Acesso: Apenas Administrador e Enfermeiro possuem permissão
     * para cadastrar, editar e excluir medicamentos e prescrições.
     */
    public static boolean podeEditarExcluir(Context context) {
        return PermissaoUtils.podeEditarMedicamento(context);
    }

    public static boolean podeEditarExcluir(String perfil) {
        PerfilUsuario p = PerfilUsuario.fromCodigo(perfil);
        return PermissaoUtils.podeEditarMedicamento(p);
    }

    public static boolean isAdministrador(Context context) {
        return SessaoManager.isAdministrador(context);
    }

    public static boolean isEnfermeiro(Context context) {
        return SessaoManager.isEnfermeiro(context);
    }

    public static boolean isCuidador(Context context) {
        return SessaoManager.isCuidador(context);
    }

    /**
     * Retorna a descrição legível do perfil.
     */
    public static String getDescricaoPerfil(Context context, String perfil) {
        if (context == null) return "";
        PerfilUsuario p = PerfilUsuario.fromCodigo(perfil);
        switch (p) {
            case ENFERMEIRO:
                return context.getString(R.string.badge_perfil_enfermeiro);
            case CUIDADOR:
                return context.getString(R.string.badge_perfil_cuidador);
            case ADMINISTRADOR:
            default:
                return context.getString(R.string.badge_perfil_admin);
        }
    }

    /**
     * Exibe o diálogo amigável de Acesso Restrito em Material Design 3 quando um cuidador
     * tenta executar ações exclusivas de Administrador/Enfermeiro.
     */
    public static void exibirDialogAcessoRestrito(Context context, String mensagemPersonalizada, Runnable onAlternadoParaAutorizado) {
        if (context == null) return;

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_aviso_perfil_restrito, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvTitulo = dialogView.findViewById(R.id.tv_dialog_titulo);
        TextView tvMsg = dialogView.findViewById(R.id.tv_dialog_mensagem);
        MaterialButton btnAlternar = dialogView.findViewById(R.id.btn_alternar_adm);
        MaterialButton btnCancelar = dialogView.findViewById(R.id.btn_cancelar_dialog);

        if (tvTitulo != null) {
            tvTitulo.setText(R.string.aviso_perfil_restrito_med_titulo);
        }

        if (tvMsg != null && mensagemPersonalizada != null && !mensagemPersonalizada.trim().isEmpty()) {
            tvMsg.setText(mensagemPersonalizada);
        }

        if (btnAlternar != null) {
            btnAlternar.setText(R.string.btn_alternar_para_adm);
            btnAlternar.setOnClickListener(v -> {
                dialog.dismiss();
                setPerfil(context, PERFIL_ADMIN);
                Toast.makeText(context, R.string.msg_perfil_admin_ativado, Toast.LENGTH_SHORT).show();
                if (onAlternadoParaAutorizado != null) {
                    onAlternadoParaAutorizado.run();
                }
            });
        }

        if (btnCancelar != null) {
            btnCancelar.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }
}
