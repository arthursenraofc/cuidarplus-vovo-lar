package com.example;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.database.MedicamentoDAO;
import com.example.databinding.ActivityMedicamentosBinding;
import com.example.model.Medicamento;
import com.example.util.EstoqueAlertaManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collections;
import java.util.List;

/**
 * MedicamentoActivity - Gerenciamento clínico de medicamentos prescritos e monitoramento de estoque.
 *
 * Responsabilidades adicionais de Monitoramento de Estoque (Material Design 3):
 * - Consulta todos os medicamentos cadastrados no banco SQLite e compara suas quantidades
 *   com um limite mínimo configurável (persistido localmente via EstoqueAlertaManager).
 * - Atualiza dinamicamente o Card de Estoque (@id/cardEstoque), o ícone de notificação
 *   (@id/ivIconeAlertaEstoque) e o badge (@id/tvBadgeAlertaEstoque), mudando sua cor para
 *   vermelho (@color/danger_red) sempre que qualquer medicamento apresentar quantidade
 *   inferior ao limite mínimo configurado, ou verde (@color/estoque_ok_fg) quando regular.
 * - Oferece diálogo Material 3 para ajuste em tempo real do limite mínimo de estoque.
 */
public class MedicamentoActivity extends ListaMedicamentosActivity {

    public static final int LIMITE_MINIMO_ESTOQUE_PADRAO = EstoqueAlertaManager.LIMITE_MINIMO_PADRAO;

    private EstoqueAlertaManager.ResumoAlertaEstoque ultimoResumoEstoque;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ultimoResumoEstoque = atualizarAlertaEstoque();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ultimoResumoEstoque = atualizarAlertaEstoque();
    }

    /**
     * Retorna o limite mínimo de estoque configurado atualmente.
     */
    public int getLimiteMinimoEstoque() {
        return EstoqueAlertaManager.getLimiteMinimoEstoque(this);
    }

    /**
     * Atualiza o limite mínimo configurável e recalcula imediatamente o estado visual do card/badge.
     */
    public boolean definirLimiteMinimoEstoque(int novoLimite) {
        boolean salvo = EstoqueAlertaManager.salvarLimiteMinimoEstoque(this, novoLimite);
        if (salvo) {
            ultimoResumoEstoque = atualizarAlertaEstoque();
        }
        return salvo;
    }

    /**
     * Retorna se o card de estoque encontra-se atualmente em estado de alerta crítico (vermelho).
     */
    public boolean isAlertaEstoqueAtivo() {
        if (ultimoResumoEstoque == null) {
            ultimoResumoEstoque = atualizarAlertaEstoque();
        }
        return ultimoResumoEstoque != null && ultimoResumoEstoque.possuiAlertaCritico();
    }

    /**
     * Retorna o último resumo calculado de monitoramento de estoque.
     */
    public EstoqueAlertaManager.ResumoAlertaEstoque getUltimoResumoEstoque() {
        return ultimoResumoEstoque;
    }

    /**
     * Consulta o banco SQLite e atualiza o ícone de notificação, badge e cores do card de estoque.
     */
    public EstoqueAlertaManager.ResumoAlertaEstoque atualizarAlertaEstoque() {
        ultimoResumoEstoque = atualizarCardEstoque(this, binding, medicamentoDAO);
        return ultimoResumoEstoque;
    }

    /**
     * Abre o diálogo Material Design 3 para configurar o limite mínimo de estoque.
     */
    public void abrirDialogConfigurarLimiteEstoque() {
        exibirDialogLimiteMinimo(this, binding, medicamentoDAO);
    }

    /**
     * Configura os eventos de clique do Card de Estoque (@id/cardEstoque) e do botão
     * de ajuste do limite mínimo (@id/btnConfigurarLimiteEstoque).
     */
    public static void configurarCardEstoque(
            AppCompatActivity activity,
            ActivityMedicamentosBinding binding,
            MedicamentoDAO medicamentoDAO
    ) {
        if (activity == null || binding == null) {
            return;
        }

        // Clique no card de estoque abre a tela detalhada de Estoque Geral
        binding.cardEstoque.setOnClickListener(v -> {
            Intent intent = new Intent(activity, EstoqueActivity.class);
            activity.startActivity(intent);
        });

        // Clique na engrenagem/ajuste abre o diálogo para configurar o limite mínimo
        binding.btnConfigurarLimiteEstoque.setOnClickListener(v -> {
            if (activity instanceof MedicamentoActivity) {
                ((MedicamentoActivity) activity).abrirDialogConfigurarLimiteEstoque();
            } else {
                exibirDialogLimiteMinimo(activity, binding, medicamentoDAO);
            }
        });

        atualizarCardEstoque(activity, binding, medicamentoDAO);
    }

    /**
     * Avalia todos os medicamentos cadastrados no SQLite frente ao limite mínimo configurável
     * e aplica as cores semânticas (vermelho para alerta de estoque baixo; verde para regular)
     * no ícone de notificação, no badge e no card de estoque.
     */
    public static EstoqueAlertaManager.ResumoAlertaEstoque atualizarCardEstoque(
            AppCompatActivity activity,
            ActivityMedicamentosBinding binding,
            MedicamentoDAO medicamentoDAO
    ) {
        int limiteMinimo = EstoqueAlertaManager.getLimiteMinimoEstoque(activity);
        if (activity == null || binding == null) {
            return new EstoqueAlertaManager.ResumoAlertaEstoque(0, limiteMinimo, Collections.emptyList());
        }

        List<Medicamento> todosMedicamentos;
        try {
            MedicamentoDAO dao = medicamentoDAO != null ? medicamentoDAO : new MedicamentoDAO(activity);
            todosMedicamentos = dao.listarTodosMedicamentos();
        } catch (Exception e) {
            todosMedicamentos = Collections.emptyList();
        }

        EstoqueAlertaManager.ResumoAlertaEstoque resumo =
                EstoqueAlertaManager.avaliarMedicamentos(todosMedicamentos, limiteMinimo);

        if (resumo.possuiAlertaCritico()) {
            // ESTADO CRÍTICO (VERMELHO): Pelo menos um medicamento possui quantidade < limiteMinimo
            int corAlerta = ContextCompat.getColor(activity, R.color.danger_red);
            int corCardBg = ContextCompat.getColor(activity, R.color.estoque_alerta_card_bg);
            int corIconeBg = ContextCompat.getColor(activity, R.color.estoque_alerta_icon_bg);
            int qtdCriticos = resumo.getQuantidadeAbaixoDoLimite();

            binding.cardEstoque.setCardBackgroundColor(corCardBg);
            binding.cardEstoque.setStrokeColor(corAlerta);

            binding.ivIconeAlertaEstoque.setImageResource(R.drawable.ic_notifications_active);
            ImageViewCompat.setImageTintList(binding.ivIconeAlertaEstoque, ColorStateList.valueOf(corAlerta));
            ViewCompat.setBackgroundTintList(binding.containerIconeAlertaEstoque, ColorStateList.valueOf(corIconeBg));

            binding.tvTituloCardEstoque.setText(R.string.card_estoque_alerta_titulo);
            binding.tvTituloCardEstoque.setTextColor(corAlerta);

            String subtitulo = qtdCriticos == 1
                    ? activity.getString(R.string.card_estoque_alerta_sub_singular, qtdCriticos, limiteMinimo)
                    : activity.getString(R.string.card_estoque_alerta_sub_plural, qtdCriticos, limiteMinimo);
            binding.tvSubtituloCardEstoque.setText(subtitulo);

            binding.tvBadgeAlertaEstoque.setText(
                    activity.getString(R.string.badge_estoque_critico_formato, qtdCriticos)
            );
            ViewCompat.setBackgroundTintList(binding.tvBadgeAlertaEstoque, ColorStateList.valueOf(corAlerta));

            // Realça também o botão rápido de Estoque para reforçar o alerta visual
            binding.btnExportarEstoquePdf.setStrokeColor(ColorStateList.valueOf(corAlerta));
            binding.btnExportarEstoquePdf.setTextColor(corAlerta);
            binding.btnExportarEstoquePdf.setIconTint(ColorStateList.valueOf(corAlerta));
        } else {
            // ESTADO NORMAL (VERDE): Nenhum medicamento está abaixo do limite mínimo configurado
            int corOk = ContextCompat.getColor(activity, R.color.estoque_ok_fg);
            int corCardBg = ContextCompat.getColor(activity, R.color.estoque_ok_card_bg);
            int corStrokeOk = ContextCompat.getColor(activity, R.color.estoque_ok_stroke);
            int corIconeBg = ContextCompat.getColor(activity, R.color.estoque_ok_icon_bg);
            int corPrimaria = ContextCompat.getColor(activity, R.color.primary);

            binding.cardEstoque.setCardBackgroundColor(corCardBg);
            binding.cardEstoque.setStrokeColor(corStrokeOk);

            binding.ivIconeAlertaEstoque.setImageResource(R.drawable.ic_notifications);
            ImageViewCompat.setImageTintList(binding.ivIconeAlertaEstoque, ColorStateList.valueOf(corOk));
            ViewCompat.setBackgroundTintList(binding.containerIconeAlertaEstoque, ColorStateList.valueOf(corIconeBg));

            if (resumo.getTotalCadastrados() == 0) {
                binding.tvTituloCardEstoque.setText(R.string.card_estoque_sem_itens_titulo);
                binding.tvSubtituloCardEstoque.setText(
                        activity.getString(R.string.card_estoque_sem_itens_sub, limiteMinimo)
                );
            } else {
                binding.tvTituloCardEstoque.setText(R.string.card_estoque_regular_titulo);
                binding.tvSubtituloCardEstoque.setText(
                        activity.getString(R.string.card_estoque_regular_sub, limiteMinimo)
                );
            }
            binding.tvTituloCardEstoque.setTextColor(corOk);

            binding.tvBadgeAlertaEstoque.setText(R.string.badge_estoque_ok);
            ViewCompat.setBackgroundTintList(binding.tvBadgeAlertaEstoque, ColorStateList.valueOf(corOk));

            binding.btnExportarEstoquePdf.setStrokeColor(ColorStateList.valueOf(corPrimaria));
            binding.btnExportarEstoquePdf.setTextColor(corPrimaria);
            binding.btnExportarEstoquePdf.setIconTint(ColorStateList.valueOf(corPrimaria));
        }

        return resumo;
    }

    /**
     * Exibe o diálogo Material Design 3 para o usuário ajustar o limite mínimo de estoque.
     */
    public static void exibirDialogLimiteMinimo(
            AppCompatActivity activity,
            ActivityMedicamentosBinding binding,
            MedicamentoDAO medicamentoDAO
    ) {
        if (activity == null) {
            return;
        }

        int limiteAtual = EstoqueAlertaManager.getLimiteMinimoEstoque(activity);

        int paddingHorizontal = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        int paddingTop = Math.round(12 * activity.getResources().getDisplayMetrics().density);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(paddingHorizontal, paddingTop, paddingHorizontal, 0);

        TextInputLayout textInputLayout = new TextInputLayout(activity);
        textInputLayout.setHint(activity.getString(R.string.hint_limite_minimo_estoque));
        textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        editText.setId(android.R.id.edit);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setText(String.valueOf(limiteAtual));
        if (editText.getText() != null) {
            editText.setSelection(editText.getText().length());
        }

        textInputLayout.addView(editText);
        container.addView(textInputLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.dialog_limite_estoque_titulo)
                .setMessage(R.string.dialog_limite_estoque_mensagem)
                .setView(container)
                .setNegativeButton(R.string.btn_cancelar, null)
                .setPositiveButton(R.string.btn_salvar_limite_estoque, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String digitado = editText.getText() != null ? editText.getText().toString().trim() : "";
            try {
                int novoLimite = Integer.parseInt(digitado);
                if (novoLimite < 1) {
                    textInputLayout.setError(activity.getString(R.string.erro_limite_estoque_invalido));
                    return;
                }

                if (activity instanceof MedicamentoActivity) {
                    ((MedicamentoActivity) activity).definirLimiteMinimoEstoque(novoLimite);
                } else {
                    EstoqueAlertaManager.salvarLimiteMinimoEstoque(activity, novoLimite);
                    atualizarCardEstoque(activity, binding, medicamentoDAO);
                }

                Toast.makeText(
                        activity,
                        activity.getString(R.string.sucesso_limite_estoque_atualizado, novoLimite),
                        Toast.LENGTH_SHORT
                ).show();
                dialog.dismiss();
            } catch (NumberFormatException ex) {
                textInputLayout.setError(activity.getString(R.string.erro_limite_estoque_invalido));
            }
        }));

        dialog.show();
    }
}
