package com.example.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.databinding.ItemPassagemTurnoBinding;
import com.example.model.PassagemTurno;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter Material Design 3 para exibição de registros de Passagem de Turno e Observações.
 */
public class PassagemTurnoAdapter extends RecyclerView.Adapter<PassagemTurnoAdapter.PassagemViewHolder> {

    public interface OnPassagemActionListener {
        void onConfirmarRecebimento(PassagemTurno passagem);
        void onExcluir(PassagemTurno passagem);
    }

    private final Context context;
    private final List<PassagemTurno> itens = new ArrayList<>();
    private final OnPassagemActionListener actionListener;
    private boolean podeExcluir = false;

    public PassagemTurnoAdapter(Context context, OnPassagemActionListener actionListener) {
        this.context = context;
        this.actionListener = actionListener;
    }

    public void setPodeExcluir(boolean podeExcluir) {
        this.podeExcluir = podeExcluir;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void atualizarLista(List<PassagemTurno> novaLista) {
        itens.clear();
        if (novaLista != null) {
            itens.addAll(novaLista);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PassagemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPassagemTurnoBinding binding = ItemPassagemTurnoBinding.inflate(
                LayoutInflater.from(context), parent, false
        );
        return new PassagemViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PassagemViewHolder holder, int position) {
        holder.bind(itens.get(position));
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    class PassagemViewHolder extends RecyclerView.ViewHolder {
        private final ItemPassagemTurnoBinding binding;

        PassagemViewHolder(@NonNull ItemPassagemTurnoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(PassagemTurno pt) {
            binding.tvBadgeTurno.setText(context.getString(R.string.formato_turno_badge, pt.getTurno()));
            binding.tvDataHoraPassagem.setText(pt.getDataHoraRegistro());
            binding.tvNomeIdosoPassagem.setText(pt.getNomeIdoso());

            String autor = pt.getProfissionalOrigem();
            if (!pt.getCargoOrigem().isEmpty()) {
                autor = autor + " • " + pt.getCargoOrigem();
            }
            binding.tvAutorPassagem.setText(context.getString(R.string.formato_registrado_por, autor));

            binding.tvObservacoesPassagem.setText(pt.getObservacoes());

            if (!pt.getIntercorrencias().trim().isEmpty()) {
                binding.tvIntercorrenciasPassagem.setVisibility(View.VISIBLE);
                binding.tvIntercorrenciasPassagem.setText(
                        context.getString(R.string.formato_intercorrencias, pt.getIntercorrencias())
                );
            } else {
                binding.tvIntercorrenciasPassagem.setVisibility(View.GONE);
            }

            if (!pt.getPendenciasProximoTurno().trim().isEmpty()) {
                binding.tvPendenciasPassagem.setVisibility(View.VISIBLE);
                binding.tvPendenciasPassagem.setText(
                        context.getString(R.string.formato_pendencias_turno, pt.getPendenciasProximoTurno())
                );
            } else {
                binding.tvPendenciasPassagem.setVisibility(View.GONE);
            }

            configurarPrioridade(pt);
            configurarConferencia(pt);

            binding.btnExcluirPassagem.setVisibility(podeExcluir ? View.VISIBLE : View.GONE);
            binding.btnExcluirPassagem.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onExcluir(pt);
                }
            });
        }

        private void configurarPrioridade(PassagemTurno pt) {
            float density = context.getResources().getDisplayMetrics().density;
            if (PassagemTurno.PRIORIDADE_URGENTE.equalsIgnoreCase(pt.getPrioridade())) {
                binding.tvBadgePrioridade.setText(R.string.prioridade_urgente);
                binding.tvBadgePrioridade.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.danger_light))
                );
                binding.tvBadgePrioridade.setTextColor(ContextCompat.getColor(context, R.color.danger_red));
                binding.cardPassagemItem.setStrokeColor(ContextCompat.getColor(context,
                        pt.isConferido() ? R.color.divider : R.color.danger_red));
                binding.cardPassagemItem.setStrokeWidth(Math.round((pt.isConferido() ? 1f : 1.5f) * density));
            } else if (PassagemTurno.PRIORIDADE_ATENCAO.equalsIgnoreCase(pt.getPrioridade())) {
                binding.tvBadgePrioridade.setText(R.string.prioridade_atencao);
                binding.tvBadgePrioridade.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.vagas_alerta_bg))
                );
                binding.tvBadgePrioridade.setTextColor(ContextCompat.getColor(context, R.color.vagas_alerta_fg));
                binding.cardPassagemItem.setStrokeColor(ContextCompat.getColor(context, R.color.divider));
                binding.cardPassagemItem.setStrokeWidth(Math.round(1f * density));
            } else {
                binding.tvBadgePrioridade.setText(R.string.prioridade_normal);
                binding.tvBadgePrioridade.setBackgroundTintList(
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md3_safe_container))
                );
                binding.tvBadgePrioridade.setTextColor(ContextCompat.getColor(context, R.color.md3_on_safe_container));
                binding.cardPassagemItem.setStrokeColor(ContextCompat.getColor(context, R.color.divider));
                binding.cardPassagemItem.setStrokeWidth(Math.round(1f * density));
            }
        }

        private void configurarConferencia(PassagemTurno pt) {
            if (pt.isConferido()) {
                binding.tvStatusConferencia.setVisibility(View.VISIBLE);
                binding.tvStatusConferencia.setText(context.getString(
                        R.string.formato_conferido_por,
                        pt.getProfissionalDestino().isEmpty() ? "Equipe" : pt.getProfissionalDestino(),
                        pt.getDataHoraConferencia()
                ));
                binding.btnConfirmarRecebimento.setVisibility(View.GONE);
            } else {
                binding.tvStatusConferencia.setVisibility(View.GONE);
                binding.btnConfirmarRecebimento.setVisibility(View.VISIBLE);
                binding.btnConfirmarRecebimento.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onConfirmarRecebimento(pt);
                    }
                });
            }
        }
    }
}
