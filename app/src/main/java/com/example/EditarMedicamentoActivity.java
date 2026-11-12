package com.example;

import android.app.TimePickerDialog;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.alarm.AlarmScheduler;
import com.example.database.IdosoDAO;
import com.example.database.MedicamentoDAO;
import com.example.databinding.ActivityEditarMedicamentoBinding;
import com.example.model.Idoso;
import com.example.model.Medicamento;
import com.example.util.PerfilManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Calendar;
import java.util.Locale;

/**
 * Activity para Edição e Refinamento de Medicamento em Material Design 3.
 * Recursos integrados:
 * 1. Controle estrito de permissões de acesso por perfil (Apenas Administrador e Enfermeiro).
 * 2. Pré-carregamento dos dados existentes com SQLite e MedicamentoDAO.
 * 3. Seleção ágil de horários via TimePicker nativo em formato 24h.
 * 4. Validação de dosagem, estoque e observações clínicas.
 * 5. Reagendamento automático de alarmes no AlarmManager no momento do salvamento.
 * 6. Tratamento nativo das permissões runtime POST_NOTIFICATIONS (API 33+) e SCHEDULE_EXACT_ALARM (API 31+).
 */
public class EditarMedicamentoActivity extends AppCompatActivity {

    public static final String EXTRA_MEDICAMENTO_ID = "EXTRA_MEDICAMENTO_ID";

    private ActivityEditarMedicamentoBinding binding;
    private MedicamentoDAO medicamentoDAO;
    private IdosoDAO idosoDAO;

    private Medicamento medicamentoAtual;
    private int medicamentoId = -1;
    private int selectedFrequenciaHoras = 8;
    private String selectedTipo = "Comprimido";

    private final int[] intervalosDisponiveis = {4, 6, 8, 12, 24};

    // Launcher nativo para permissão de notificações em tempo de execução (Android 13+ / API 33+)
    private final ActivityResultLauncher<String> requestNotificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Toast.makeText(this, "Permissão concedida: Alertas e notificações ativados.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Aviso: Sem permissão de notificação, os alarmes não emitirão avisos na barra de status.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditarMedicamentoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        medicamentoDAO = new MedicamentoDAO(this);
        idosoDAO = new IdosoDAO(this);

        if (!verificarControleAcessoPerfil()) {
            return;
        }

        recuperarMedicamento();
        configurarToolbar();
        configurarCamposDropdown();
        configurarTimePicker();
        configurarBotoes();
        verificarPermissoesRuntime();
    }

    /**
     * Regra de Acesso: Apenas Administradores e Enfermeiros podem editar ou excluir medicamentos.
     * Caso o usuário esteja no perfil Cuidador, bloqueia e oferece alternar ou retornar.
     */
    private boolean verificarControleAcessoPerfil() {
        if (!PerfilManager.podeEditarExcluir(this)) {
            PerfilManager.exibirDialogAcessoRestrito(
                    this,
                    getString(R.string.aviso_perfil_restrito_med_msg),
                    () -> {
                        // Usuário alternou para ADMIN, recarrega
                        recreate();
                    }
            );
            return false;
        }
        return true;
    }

    private void recuperarMedicamento() {
        medicamentoId = getIntent().getIntExtra(EXTRA_MEDICAMENTO_ID, -1);
        if (medicamentoId <= 0) {
            Toast.makeText(this, "Identificador de medicamento inválido.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        medicamentoAtual = medicamentoDAO.buscarPorId(medicamentoId);
        if (medicamentoAtual == null) {
            Toast.makeText(this, "Medicamento não encontrado no banco de dados.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        preencherDadosInterface();
    }

    private void preencherDadosInterface() {
        // Nome do residente no cabeçalho
        String nomeResidente = medicamentoAtual.getNomeIdoso();
        if (nomeResidente == null || nomeResidente.isEmpty()) {
            Idoso idoso = idosoDAO.buscarPorId(medicamentoAtual.getIdIdoso());
            if (idoso != null) {
                nomeResidente = idoso.getNome();
            }
        }
        binding.tvNomeResidenteHeader.setText(getString(R.string.formato_dosagem_rotulo, nomeResidente != null ? nomeResidente : "Residente"));

        // Campos do medicamento
        binding.etNomeMedicamento.setText(medicamentoAtual.getNomeMedicamento());
        binding.etDosagem.setText(medicamentoAtual.getDosagem());
        binding.etEstoque.setText(medicamentoAtual.getEstoque());
        binding.etHorarioInicial.setText(medicamentoAtual.getHorarioInicial());
        binding.etObservacao.setText(medicamentoAtual.getObservacaoMedica());
        binding.switchStatusAtivo.setChecked(medicamentoAtual.isAtivo());

        selectedTipo = medicamentoAtual.getTipo();
        binding.actvTipo.setText(selectedTipo, false);

        selectedFrequenciaHoras = medicamentoAtual.getFrequenciaHoras() > 0 ? medicamentoAtual.getFrequenciaHoras() : 8;
        String frequenciaFormatada = getString(R.string.formato_intervalo_horas, selectedFrequenciaHoras);
        binding.actvFrequencia.setText(frequenciaFormatada, false);
    }

    private void configurarToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void configurarCamposDropdown() {
        // 1. Dropdown de Tipos Farmacêuticos
        String[] tipos = {
                getString(R.string.tipo_comprimido),
                getString(R.string.tipo_gotas),
                getString(R.string.tipo_injecao),
                getString(R.string.tipo_pomada),
                getString(R.string.tipo_xarope)
        };
        ArrayAdapter<String> tipoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, tipos);
        binding.actvTipo.setAdapter(tipoAdapter);
        binding.actvTipo.setOnItemClickListener((parent, view, position, id) -> {
            selectedTipo = tipos[position];
        });

        // 2. Dropdown de Frequências em Horas
        String[] frequenciasLabels = {
                getString(R.string.frequencia_4h),
                getString(R.string.frequencia_6h),
                getString(R.string.frequencia_8h),
                getString(R.string.frequencia_12h),
                getString(R.string.frequencia_24h)
        };
        ArrayAdapter<String> freqAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, frequenciasLabels);
        binding.actvFrequencia.setAdapter(freqAdapter);
        binding.actvFrequencia.setOnItemClickListener((parent, view, position, id) -> {
            selectedFrequenciaHoras = intervalosDisponiveis[position];
        });
    }

    private void configurarTimePicker() {
        View.OnClickListener timePickerListener = v -> {
            Calendar c = Calendar.getInstance();
            int horaAtual = c.get(Calendar.HOUR_OF_DAY);
            int minutoAtual = c.get(Calendar.MINUTE);

            String textoAtual = binding.etHorarioInicial.getText() != null ? binding.etHorarioInicial.getText().toString().trim() : "";
            if (textoAtual.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
                String[] partes = textoAtual.split(":");
                horaAtual = Integer.parseInt(partes[0].trim());
                minutoAtual = Integer.parseInt(partes[1].trim());
            }

            TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                String horaFormatada = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                binding.etHorarioInicial.setText(horaFormatada);
                binding.tilHorarioInicial.setError(null);
            }, horaAtual, minutoAtual, true);

            dialog.show();
        };

        binding.etHorarioInicial.setOnClickListener(timePickerListener);
        binding.tilHorarioInicial.setEndIconOnClickListener(timePickerListener);
    }

    private void configurarBotoes() {
        binding.btnSalvarAlteracoes.setOnClickListener(v -> validarESalvar());

        binding.btnExcluirMedicamento.setOnClickListener(v -> confirmarExclusao());

        binding.btnCancelar.setOnClickListener(v -> finish());
    }

    private void validarESalvar() {
        String nome = binding.etNomeMedicamento.getText() != null ? binding.etNomeMedicamento.getText().toString().trim() : "";
        String dosagem = binding.etDosagem.getText() != null ? binding.etDosagem.getText().toString().trim() : "";
        String estoque = binding.etEstoque.getText() != null ? binding.etEstoque.getText().toString().trim() : "";
        String horario = binding.etHorarioInicial.getText() != null ? binding.etHorarioInicial.getText().toString().trim() : "";
        String observacao = binding.etObservacao.getText() != null ? binding.etObservacao.getText().toString().trim() : "";
        boolean ativo = binding.switchStatusAtivo.isChecked();

        boolean valido = true;

        if (nome.isEmpty()) {
            binding.tilNomeMedicamento.setError("Informe o nome do medicamento");
            valido = false;
        } else {
            binding.tilNomeMedicamento.setError(null);
        }

        if (dosagem.isEmpty()) {
            binding.tilDosagem.setError("Informe a dosagem");
            valido = false;
        } else {
            binding.tilDosagem.setError(null);
        }

        if (horario.isEmpty() || !horario.matches("^([01]?\\d|2[0-3]):[0-5]\\d$")) {
            binding.tilHorarioInicial.setError("Selecione um horário válido (HH:mm)");
            valido = false;
        } else {
            binding.tilHorarioInicial.setError(null);
        }

        if (!valido) {
            Toast.makeText(this, "Por favor, preencha os campos obrigatórios corretamente.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Atualiza os dados do modelo
        medicamentoAtual.setNomeMedicamento(nome);
        medicamentoAtual.setDosagem(dosagem);
        medicamentoAtual.setEstoque(estoque);
        medicamentoAtual.setTipo(selectedTipo);
        medicamentoAtual.setHorarioInicial(horario);
        medicamentoAtual.setFrequenciaHoras(selectedFrequenciaHoras);
        medicamentoAtual.setObservacaoMedica(observacao);
        medicamentoAtual.setStatusAtivo(ativo ? 1 : 0);

        // Salva no SQLite e reagenda o alarme no AlarmManager automaticamente via DAO
        int linhas = medicamentoDAO.atualizarMedicamento(medicamentoAtual);
        if (linhas > 0) {
            Toast.makeText(this, R.string.sucesso_medicamento_atualizado, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Erro ao salvar alterações no banco de dados.", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmarExclusao() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirmar_exclusao_med_titulo)
                .setMessage(getString(R.string.confirmar_exclusao_med_msg, medicamentoAtual.getNomeMedicamento()))
                .setPositiveButton(R.string.excluir, (dialog, which) -> {
                    int deletados = medicamentoDAO.excluirMedicamento(medicamentoAtual.getId());
                    if (deletados > 0) {
                        Toast.makeText(this, R.string.sucesso_medicamento_excluido, Toast.LENGTH_LONG).show();
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        Toast.makeText(this, "Erro ao excluir medicamento.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancelar, null)
                .show();
    }

    /**
     * Tratamento nativo de permissões POST_NOTIFICATIONS (Android 13+) e SCHEDULE_EXACT_ALARM (Android 12+).
     */
    private void verificarPermissoesRuntime() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permissao_notificacao_titulo)
                        .setMessage(R.string.dialog_permissao_notificacao_msg)
                        .setPositiveButton(R.string.btn_autorizar, (d, w) -> {
                            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                        })
                        .setNegativeButton(R.string.btn_cancelar, null)
                        .show();
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!AlarmScheduler.podeAgendarAlarmesExatos(this)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.dialog_permissao_alarme_titulo)
                        .setMessage(R.string.dialog_permissao_alarme_msg)
                        .setPositiveButton(R.string.btn_abrir_configuracoes, (d, w) -> {
                            AlarmScheduler.abrirConfiguracaoAlarmeExato(this);
                        })
                        .setNegativeButton(R.string.btn_cancelar, null)
                        .show();
            }
        }
    }
}
