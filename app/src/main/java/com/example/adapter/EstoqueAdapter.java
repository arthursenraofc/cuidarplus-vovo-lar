package com.example.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.Medicamento;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter para visualização do inventário consolidado de medicamentos da instituição.
 */
public class EstoqueAdapter extends RecyclerView.Adapter<EstoqueAdapter.EstoqueViewHolder> {

    public interface OnWhatsAppReposicaoClickListener {
        void onReposicaoClick(Medicamento medicamento);
    }

    private final Context context;
    private List<Medicamento> listaCompleta;
    private List<Medicamento> listaFiltrada;
    private final OnWhatsAppReposicaoClickListener reposicaoClickListener;

    public EstoqueAdapter(Context context, List<Medicamento> medicamentos, OnWhatsAppReposicaoClickListener listener) {
        this.context = context;
        this.listaCompleta = medicamentos != null ? new ArrayList<>(medicamentos) : new ArrayList<>();
        this.listaFiltrada = new ArrayList<>(this.listaCompleta);
        this.reposicaoClickListener = listener;
    }

    public void atualizarLista(List<Medicamento> novaLista) {
        this.listaCompleta = novaLista != null ? new ArrayList<>(novaLista) : new ArrayList<>();
        this.listaFiltrada = new ArrayList<>(this.listaCompleta);
        notifyDataSetChanged();
    }

    public void filtrar(String query) {
        listaFiltrada.clear();
        if (query == null || query.trim().isEmpty()) {
            listaFiltrada.addAll(listaCompleta);
        } else {
            String q = query.toLowerCase().trim();
            for (Medicamento m : listaCompleta) {
                String nome = m.getNomeMedicamento() != null ? m.getNomeMedicamento().toLowerCase() : "";
                String idoso = m.getNomeIdoso() != null ? m.getNomeIdoso().toLowerCase() : "";
                if (nome.contains(q) || idoso.contains(q)) {
                    listaFiltrada.add(m);
                }
            }
        }
        notifyDataSetChanged();
    }

    public List<Medicamento> getItensExibidos() {
        return listaFiltrada;
    }

    @NonNull
    @Override
    public EstoqueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_estoque, parent, false);
        return new EstoqueViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EstoqueViewHolder holder, int position) {
        Medicamento med = listaFiltrada.get(position);

        holder.tvNomeMed.setText(med.getNomeMedicamento());
        String dosagem = (med.getDosagem() != null ? med.getDosagem() : "-")
                + " • " + (med.getTipo() != null ? med.getTipo() : "Comprimido");
        holder.tvDosagem.setText(dosagem);

        String estoqueStr = med.getEstoque() != null && !med.getEstoque().trim().isEmpty()
                ? med.getEstoque() : "Estoque não informado";
        holder.tvEstoqueQtd.setText("Estoque: " + estoqueStr);

        String responsavel = med.getNomeIdoso() != null && !med.getNomeIdoso().trim().isEmpty()
                ? med.getNomeIdoso() : "Geral / Sem residente vinculado";
        holder.tvResponsavel.setText("Residente: " + responsavel);

        // Data de Validade e Badge de Alerta de Vencimento (Vermelho vencido / Laranja <= 30 dias)
        String validade = med.getDataValidade();
        if (!validade.isEmpty()) {
            holder.tvDataValidade.setText(context.getString(R.string.rotulo_validade_formato, validade));
        } else {
            holder.tvDataValidade.setText(R.string.validade_nao_informada);
        }

        com.example.util.ValidadeMedicamentoHelper.AvaliacaoValidade avaliacaoValidade =
                com.example.util.ValidadeMedicamentoHelper.avaliarValidade(validade);

        if (avaliacaoValidade.possuiAlerta()) {
            holder.tvBadgeValidade.setVisibility(View.VISIBLE);
            holder.tvBadgeValidade.setText(
                    com.example.util.ValidadeMedicamentoHelper.formatarTextoBadge(avaliacaoValidade)
            );
            if (avaliacaoValidade.getStatus() == com.example.util.ValidadeMedicamentoHelper.StatusValidade.VENCIDO) {
                holder.tvBadgeValidade.setBackgroundColor(ContextCompat.getColor(context, R.color.danger_red));
                holder.tvDataValidade.setTextColor(ContextCompat.getColor(context, R.color.danger_red));
            } else {
                holder.tvBadgeValidade.setBackgroundColor(ContextCompat.getColor(context, R.color.validade_alerta_bg));
                holder.tvDataValidade.setTextColor(ContextCompat.getColor(context, R.color.validade_alerta_fg));
            }
        } else {
            holder.tvBadgeValidade.setVisibility(View.GONE);
            holder.tvDataValidade.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Status badge do estoque
        String status = determinarStatus(med);
        holder.tvBadgeStatus.setText(status);

        boolean mostrarWhatsApp = avaliacaoValidade.possuiAlerta();
        if ("Estoque Crítico".equals(status)) {
            holder.tvBadgeStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.danger_red));
            mostrarWhatsApp = true;
        } else if ("Estoque Baixo".equals(status) || "Atenção".equals(status)) {
            holder.tvBadgeStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.validade_alerta_bg));
            mostrarWhatsApp = true;
        } else {
            holder.tvBadgeStatus.setBackgroundColor(ContextCompat.getColor(context, R.color.success_green));
        }
        holder.btnWhatsApp.setVisibility(mostrarWhatsApp ? View.VISIBLE : View.GONE);

        holder.btnWhatsApp.setOnClickListener(v -> {
            if (reposicaoClickListener != null) {
                reposicaoClickListener.onReposicaoClick(med);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listaFiltrada.size();
    }

    private String determinarStatus(Medicamento med) {
        int limiteMinimo = com.example.util.EstoqueAlertaManager.getLimiteMinimoEstoque(context);
        if (com.example.util.EstoqueAlertaManager.estaAbaixoDoLimiteMinimo(med, limiteMinimo)) {
            return "Estoque Crítico";
        }

        int qtd = com.example.util.EstoqueAlertaManager.extrairQuantidadeNumerica(med.getEstoque());
        if (qtd <= limiteMinimo + 5) {
            return context.getString(R.string.status_estoque_baixo);
        }
        return "Normal";
    }

    static class EstoqueViewHolder extends RecyclerView.ViewHolder {
        final TextView tvNomeMed;
        final TextView tvDosagem;
        final TextView tvBadgeStatus;
        final TextView tvBadgeValidade;
        final TextView tvEstoqueQtd;
        final TextView tvResponsavel;
        final TextView tvDataValidade;
        final MaterialButton btnWhatsApp;

        public EstoqueViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNomeMed = itemView.findViewById(R.id.tv_item_nome_medicamento);
            tvDosagem = itemView.findViewById(R.id.tv_item_dosagem);
            tvBadgeStatus = itemView.findViewById(R.id.tv_item_badge_status);
            tvBadgeValidade = itemView.findViewById(R.id.tv_item_badge_validade);
            tvEstoqueQtd = itemView.findViewById(R.id.tv_item_estoque_qtd);
            tvResponsavel = itemView.findViewById(R.id.tv_item_responsavel);
            tvDataValidade = itemView.findViewById(R.id.tv_item_data_validade);
            btnWhatsApp = itemView.findViewById(R.id.btn_item_whatsapp_reposicao);
        }
    }
}
