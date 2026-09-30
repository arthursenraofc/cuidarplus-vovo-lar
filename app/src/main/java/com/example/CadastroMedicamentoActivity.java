package com.example;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.alarm.AlarmScheduler;
import com.example.database.DatabaseHelper;
import com.example.database.IdosoDAO;
import com.example.databinding.ActivityCadastroMedicamentoBinding;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Activity para Cadastro de Medicamento (Sprint 2 Avançado).
 * Suporta formulário intuitivo com TimePicker nativo para hora inicial,
 * dropdown para frequência e tipos, observações médicas, validações estritas
 * e solicitação amigável de permissões de alarmes exatos do Android.
 */
public class CadastroMedicamentoActivity extends AppCompatActivity {

    public static final String EXTRA_PRESELECTED_IDOSO_ID = "EXTRA_PRESELECTED_IDOSO_ID";

    private ActivityCadastroMedicamentoBinding binding;
    private DatabaseHelper dbHelper;
    private IdosoDAO idosoDAO;

    private List<Idoso> listaIdosos = new ArrayList<>();
    private int selectedIdosoId = -1;
    private int selectedFrequenciaHoras = 8; // Padrão: 8 horas
    private String selectedTipo = "Comprimido";

    private final int[] intervalosDisponiveis = {4, 6, 8, 12, 24};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCadastroMedicamentoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dbHelper = DatabaseHelper.getInstance(this);
        idosoDAO = new IdosoDAO(this);

        configurarToolbar();
        carregarIdosos();
        configurarCampos();
        configurarBotoes();
        verificarPermissaoAlarmeExato();
    }

    private void configurarToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void carregarIdosos() {
        listaIdosos = idosoDAO.listarTodos();
        List<String> nomes = new ArrayList<>();
        int preselectedId = getIntent().getIntExtra(EXTRA_PRESELECTED_IDOSO_ID, -1);
        int preselectedIndex = -1;

        for (int i = 0; i < listaIdosos.size(); i++) {
            Idoso idoso = listaIdosos.get(i);
            nomes.add(idoso.getNome());
            if (idoso.getId() == preselectedId) {
                preselectedIndex = i;
                selectedIdosoId = idoso.getId();
            }
        }

        ArrayAdapter<String> adapterIdosos = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, nomes
        );
        binding.actvSelecionarIdoso.setAdapter(adapterIdosos);

        if (preselectedIndex >= 0 && preselectedIndex < nomes.size()) {
            binding.actvSelecionarIdoso.setText(nomes.get(preselectedIndex), false);
        }

        binding.actvSelecionarIdoso.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < listaIdosos.size()) {
                selectedIdosoId = listaIdosos.get(position).getId();
                binding.tilIdoso.setError(null);
            }
        });
    }

    private void configurarCampos() {
        // 1. Tipos / Formas farmacêuticas
        List<String> tipos = new ArrayList<>();
        tipos.add(getString(R.string.tipo_comprimido));
        tipos.add(getString(R.string.tipo_gotas));
        tipos.add(getString(R.string.tipo_injecao));
        tipos.add(getString(R.string.tipo_pomada));
        tipos.add(getString(R.string.tipo_xarope));

        ArrayAdapter<String> adapterTipos = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, tipos
        );
        binding.actvTipo.setAdapter(adapterTipos);
        binding.actvTipo.setText(tipos.get(0), false);
        selectedTipo = tipos.get(0);

        binding.actvTipo.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < tipos.size()) {
                selectedTipo = tipos.get(position);
            }
        });

        // 2. Horário Inicial (TimePicker nativo)
        binding.etHorarioInicial.setOnClickListener(v -> exibirTimePicker());

        // 3. Frequência em horas
        List<String> labelsFrequencia = new ArrayList<>();
        labelsFrequencia.add(getString(R.string.frequencia_4h));
        labelsFrequencia.add(getString(R.string.frequencia_6h));
        labelsFrequencia.add(getString(R.string.frequencia_8h));
        labelsFrequencia.add(getString(R.string.frequencia_12h));
        labelsFrequencia.add(getString(R.string.frequencia_24h));

        ArrayAdapter<String> adapterFreq = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, labelsFrequencia
        );
        binding.actvFrequencia.setAdapter(adapterFreq);
        binding.actvFrequencia.setText(labelsFrequencia.get(2), false); // 8h padrão

        binding.actvFrequencia.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < intervalosDisponiveis.length) {
                selectedFrequenciaHoras = intervalosDisponiveis[position];
            }
        });
    }

    private void exibirTimePicker() {
        Calendar cal = Calendar.getInstance();
        int horaAtual = cal.get(Calendar.HOUR_OF_DAY);
        int minutoAtual = cal.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    String horarioFormatado = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                    binding.etHorarioInicial.setText(horarioFormatado);
                    binding.tilHorarioInicial.setError(null);
                },
                horaAtual,
                minutoAtual,
                true // 24 horas
        );
        timePickerDialog.show();
    }

    private void configurarBotoes() {
        binding.btnSalvarMedicamento.setOnClickListener(v -> salvarMedicamento());
        binding.btnCancelar.setOnClickListener(v -> finish());
    }

    private void salvarMedicamento() {
        String nomeMed = binding.etNomeMedicamento.getText() != null
                ? binding.etNomeMedicamento.getText().toString().trim() : "";
        String dosagem = binding.etDosagem.getText() != null
                ? binding.etDosagem.getText().toString().trim() : "";
        String horario = binding.etHorarioInicial.getText() != null
                ? binding.etHorarioInicial.getText().toString().trim() : "";
        String observacao = binding.etObservacao.getText() != null
                ? binding.etObservacao.getText().toString().trim() : "";

        boolean valido = true;

        if (selectedIdosoId <= 0) {
            binding.tilIdoso.setError("Selecione o residente.");
            valido = false;
        } else {
            binding.tilIdoso.setError(null);
        }

        if (nomeMed.isEmpty()) {
            binding.tilNomeMedicamento.setError("Informe o nome do medicamento.");
            valido = false;
        } else {
            binding.tilNomeMedicamento.setError(null);
        }

        if (dosagem.isEmpty()) {
            binding.tilDosagem.setError("Informe a dosagem.");
            valido = false;
        } else {
            binding.tilDosagem.setError(null);
        }

        if (horario.isEmpty()) {
            binding.tilHorarioInicial.setError("Selecione o horário inicial.");
            valido = false;
        } else {
            binding.tilHorarioInicial.setError(null);
        }

        if (!valido) {
            return;
        }

        // Criar entidade Medicamento
        Medicamento med = new Medicamento();
        med.setIdIdoso(selectedIdosoId);
        med.setNomeMedicamento(nomeMed);
        med.setDosagem(dosagem);
        med.setTipo(selectedTipo);
        med.setHorarioInicial(horario);
        med.setFrequenciaHoras(selectedFrequenciaHoras);
        med.setObservacaoMedica(observacao);
        med.setStatusAtivo(1);

        Idoso idoso = idosoDAO.buscarPorId(selectedIdosoId);
        if (idoso != null) {
            med.setNomeIdoso(idoso.getNome());
        }

        long idGerado = dbHelper.cadastrarMedicamento(med);
        if (idGerado != -1) {
            med.setId((int) idGerado);
            // Agendar alarme nativo com AlarmManager
            AlarmScheduler.agendarMedicamento(this, med);

            Toast.makeText(this, R.string.sucesso_medicamento_salvo, Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Erro ao salvar medicamento no SQLite.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Checa permissão de alarme exato (Android 12+) e convida o usuário amigavelmente se necessário.
     */
    private void verificarPermissaoAlarmeExato() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!AlarmScheduler.podeAgendarAlarmesExatos(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permissao_alarme_titulo)
                        .setMessage(R.string.dialog_permissao_alarme_msg)
                        .setPositiveButton(R.string.btn_abrir_configuracoes, (dialog, which) -> {
                            AlarmScheduler.abrirConfiguracaoAlarmeExato(this);
                        })
                        .setNegativeButton(R.string.btn_cancelar, null)
                        .show();
            }
        }
    }
}
