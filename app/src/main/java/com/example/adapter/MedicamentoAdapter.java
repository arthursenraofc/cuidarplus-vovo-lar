package com.example.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.ItemMedicamentoBinding;
import com.example.model.DoseAlertaDTO;
import com.example.model.Medicamento;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter para RecyclerView de Medicamentos (Sprint 2 Avançado).
 * Apresenta Badge visual de status:
 * - Verde ("No Horário"): dose prevista ainda válida.
 * - Vermelho ("Atrasado"): passou do horário previsto sem confirmação de baixa.
 * - Cinza ("Concluído"): dose do dia já administrada.
 */
public class MedicamentoAdapter extends RecyclerView.Adapter<MedicamentoAdapter.MedicamentoViewHolder> {

    public interface OnAdministrarClickListener {
        void onAdministrarClick(DoseAlertaDTO doseDTO);
    }

    private final Context context;
    private final List<DoseAlertaDTO> listaDoses = new ArrayList<>();
    private final OnAdministrarClickListener listener;

    public MedicamentoAdapter(Context context, List<DoseAlertaDTO> listaDoses, OnAdministrarClickListener listener) {
        this.context = context;
        if (listaDoses != null) {
            this.listaDoses.addAll(listaDoses);
        }
        this.listener = listener;
    }

    @NonNull
    @Override
    public MedicamentoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMedicamentoBinding binding = ItemMedicamentoBinding.inflate(
                LayoutInflater.from(context), parent, false
        );
        return new MedicamentoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MedicamentoViewHolder holder, int position) {
        DoseAlertaDTO item = listaDoses.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return listaDoses.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void atualizarLista(List<DoseAlertaDTO> novaLista) {
        this.listaDoses.clear();
        if (novaLista != null) {
            this.listaDoses.addAll(novaLista);
        }
        notifyDataSetChanged();
    }

    class MedicamentoViewHolder extends RecyclerView.ViewHolder {
        private final ItemMedicamentoBinding binding;

        public MedicamentoViewHolder(@NonNull ItemMedicamentoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(DoseAlertaDTO dto) {
            Medicamento med = dto.getMedicamento();
            binding.tvNomeMedicamento.setText(med.getNomeMedicamento());

            // Dosagem e tipo farmacêutico
            String dosagemTipo = "Dose: " + med.getDosagem() + " • " + med.getTipo();
            binding.tvDosagemTipo.setText(dosagemTipo);

            // Horário previsto e intervalo
            String intervaloTexto = context.getString(R.string.formato_intervalo_horas, med.getFrequenciaHoras());
            String agendamento = "Previsto: " + dto.getHorarioPrevisto() + " • " + intervaloTexto;
            binding.tvHorarioEFrequencia.setText(agendamento);

            // Observação médica
            if (med.getObservacaoMedica() != null && !med.getObservacaoMedica().trim().isEmpty()) {
                binding.tvObservacaoMedica.setVisibility(View.VISIBLE);
                binding.tvObservacaoMedica.setText(context.getString(R.string.label_observacao_formato, med.getObservacaoMedica()));
            } else {
                binding.tvObservacaoMedica.setVisibility(View.GONE);
            }

            // Configuração do Badge Visual de Status
            configurarBadgeStatus(dto);

            // Botão "Dar Baixa Agora"
            if (dto.isAdministradoHoje()) {
                binding.btnDarBaixaAgora.setEnabled(false);
                binding.btnDarBaixaAgora.setText(R.string.badge_status_concluido);
                binding.btnDarBaixaAgora.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.surface_variant));
                binding.btnDarBaixaAgora.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
                binding.btnDarBaixaAgora.setIconTint(ContextCompat.getColorStateList(context, R.color.text_muted));
            } else {
                binding.btnDarBaixaAgora.setEnabled(true);
                binding.btnDarBaixaAgora.setText(R.string.btn_dar_baixa_agora);
                binding.btnDarBaixaAgora.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.primary));
                binding.btnDarBaixaAgora.setTextColor(ContextCompat.getColor(context, R.color.white));
                binding.btnDarBaixaAgora.setIconTint(ContextCompat.getColorStateList(context, R.color.white));
                binding.btnDarBaixaAgora.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAdministrarClick(dto);
                    }
                });
            }
        }

        private void configurarBadgeStatus(DoseAlertaDTO dto) {
            String status = dto.getStatusPontualidade();
            if ("CONCLUIDO".equalsIgnoreCase(status)) {
                binding.tvBadgeStatus.setBackgroundResource(R.drawable.bg_badge_concluido);
                binding.tvBadgeStatus.setText(R.string.badge_status_concluido);
                binding.tvBadgeStatus.setTextColor(Color.parseColor("#5F6368"));
            } else if ("ATRASADO".equalsIgnoreCase(status)) {
                binding.tvBadgeStatus.setBackgroundResource(R.drawable.bg_badge_atrasado);
                binding.tvBadgeStatus.setText(R.string.badge_status_atrasado);
                binding.tvBadgeStatus.setTextColor(Color.parseColor("#C5221F"));
            } else {
                // "NO_HORARIO"
                binding.tvBadgeStatus.setBackgroundResource(R.drawable.bg_badge_no_horario);
                binding.tvBadgeStatus.setText(R.string.badge_status_no_horario);
                binding.tvBadgeStatus.setTextColor(Color.parseColor("#137333"));
            }
        }
    }
}
