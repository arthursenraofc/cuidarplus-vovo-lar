package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.Idoso;
import com.example.util.ImageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter em Java para carregar a lista de idosos no RecyclerView.
 * Exibe foto circular, nome, idade calculada, tipo sanguíneo, alertas de alergias
 * e um botão discreto de "Ver Ficha Completa".
 */
public class IdosoAdapter extends RecyclerView.Adapter<IdosoAdapter.IdosoViewHolder> {

    public interface OnIdosoClickListener {
        void onIdosoClick(Idoso idoso);
    }

    private List<Idoso> listaIdosos = new ArrayList<>();
    private final OnIdosoClickListener listener;

    public IdosoAdapter(OnIdosoClickListener listener) {
        this.listener = listener;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setLista(List<Idoso> novaLista) {
        this.listaIdosos = novaLista != null ? novaLista : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public IdosoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_idoso, parent, false);
        return new IdosoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IdosoViewHolder holder, int position) {
        Idoso idoso = listaIdosos.get(position);
        holder.bind(idoso, listener);
    }

    @Override
    public int getItemCount() {
        return listaIdosos.size();
    }

    public static class IdosoViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivFoto;
        private final TextView tvNome;
        private final TextView tvBadgeSangue;
        private final TextView tvIdadeData;
        private final TextView tvContatoResumo;
        private final View layoutAlertaMedico;
        private final TextView tvResumoAlergiasRestricoes;
        private final TextView btnVerFichaCompleta;

        public IdosoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFoto = itemView.findViewById(R.id.iv_foto_idoso);
            tvNome = itemView.findViewById(R.id.tv_nome_idoso);
            tvBadgeSangue = itemView.findViewById(R.id.tv_badge_tipo_sanguineo);
            tvIdadeData = itemView.findViewById(R.id.tv_idade_data);
            tvContatoResumo = itemView.findViewById(R.id.tv_contato_resumo);
            layoutAlertaMedico = itemView.findViewById(R.id.layout_alerta_medico);
            tvResumoAlergiasRestricoes = itemView.findViewById(R.id.tv_resumo_alergias_restricoes);
            btnVerFichaCompleta = itemView.findViewById(R.id.btn_ver_ficha_completa);
        }

        public void bind(final Idoso idoso, final OnIdosoClickListener listener) {
            // Nome do Idoso
            tvNome.setText(idoso.getNome());

            // Tipo Sanguíneo em destaque
            String tipoSangue = idoso.getTipoSanguineo();
            if (tipoSangue != null && !tipoSangue.trim().isEmpty() && !tipoSangue.equalsIgnoreCase("Não informado")) {
                tvBadgeSangue.setVisibility(View.VISIBLE);
                tvBadgeSangue.setText(tipoSangue);
            } else {
                tvBadgeSangue.setVisibility(View.GONE);
            }

            // Idade calculada e Data de Nascimento
            Context context = itemView.getContext();
            int idade = idoso.calcularIdade();
            String dataNasc = idoso.getDataNascimento();
            if (idade > 0) {
                tvIdadeData.setText(context.getString(R.string.idade_e_nascimento, idade, (dataNasc != null ? dataNasc : "-")));
            } else if (dataNasc != null && !dataNasc.isEmpty()) {
                tvIdadeData.setText(context.getString(R.string.formato_nascimento, dataNasc));
            } else {
                tvIdadeData.setText(context.getString(R.string.idade_nao_informada));
            }

            // Contato de Emergência resumido
            if (idoso.getContatoEmergencia() != null && !idoso.getContatoEmergencia().trim().isEmpty()) {
                tvContatoResumo.setVisibility(View.VISIBLE);
                tvContatoResumo.setText(context.getString(R.string.formato_emergencia, idoso.getContatoEmergencia()));
            } else {
                tvContatoResumo.setVisibility(View.GONE);
            }

            // Foto Circular do Idoso (com fallback seguro para placeholder)
            ImageUtil.carregarFoto(ivFoto, idoso.getCaminhoFoto());

            // Faixa de Alertas e Restrições Médicas
            StringBuilder alerta = new StringBuilder();
            if (idoso.getAlergias() != null && !idoso.getAlergias().trim().isEmpty()
                    && !idoso.getAlergias().toLowerCase(Locale.ROOT).contains("nenhuma")) {
                alerta.append("⚠️ ").append(idoso.getAlergias());
            }
            if (idoso.getRestricoesMedicas() != null && !idoso.getRestricoesMedicas().trim().isEmpty()
                    && !idoso.getRestricoesMedicas().toLowerCase(Locale.ROOT).contains("nenhuma")) {
                if (alerta.length() > 0) alerta.append(" | ");
                alerta.append(idoso.getRestricoesMedicas());
            }

            if (alerta.length() > 0) {
                layoutAlertaMedico.setVisibility(View.VISIBLE);
                tvResumoAlergiasRestricoes.setText(alerta.toString());
            } else {
                layoutAlertaMedico.setVisibility(View.GONE);
            }

            // Clique tanto no Card quanto no botão "Ver Ficha Completa"
            View.OnClickListener clickAction = v -> {
                if (listener != null) {
                    listener.onIdosoClick(idoso);
                }
            };

            itemView.setOnClickListener(clickAction);
            if (btnVerFichaCompleta != null) {
                btnVerFichaCompleta.setOnClickListener(clickAction);
            }
        }
    }
}
