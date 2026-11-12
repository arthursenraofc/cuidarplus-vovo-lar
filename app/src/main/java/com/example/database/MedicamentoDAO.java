package com.example.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.alarm.AlarmScheduler;
import com.example.model.DoseAlertaDTO;
import com.example.model.HistoricoMedicacao;
import com.example.model.Medicamento;
import com.example.model.PerfilUsuario;
import com.example.util.PermissaoUtils;
import com.example.util.SessaoManager;

import java.util.List;

/**
 * Classe DAO (Data Access Object) responsável pelas operações de CRUD na tabela de medicamentos.
 * Contém travas de segurança de nível de persistência (RBAC) para garantir que
 * operações de inclusão, alteração e exclusão somente sejam efetuadas por perfis clínicos autorizados
 * (ADMINISTRADOR ou ENFERMEIRO).
 */
public class MedicamentoDAO {

    private static final String TAG = "MedicamentoDAO";
    private final DatabaseHelper dbHelper;
    private final Context context;

    public MedicamentoDAO(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = DatabaseHelper.getInstance(context);
    }

    /**
     * Insere um novo medicamento validando o perfil ativo na sessão.
     */
    public long inserir(Medicamento med) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return inserir(med, perfilAtivo);
    }

    /**
     * Insere um novo medicamento com validação estrita de segurança RBAC.
     * @throws AcessoNaoAutorizadoException se o perfil não tiver permissão para cadastrar medicamentos
     */
    public long inserir(Medicamento med, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeCadastrarMedicamento(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de cadastrar medicamento por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Cadastrar Medicamento");
        }

        long id = dbHelper.cadastrarMedicamento(med);
        if (id != -1) {
            med.setId((int) id);
            // Regra de Negócio: agenda alarme nativo caso ativo
            if (med.isAtivo()) {
                AlarmScheduler.agendarMedicamento(context, med);
                Log.d(TAG, "Alarme agendado para novo medicamento: " + med.getNomeMedicamento());
            }
        }
        return id;
    }

    /**
     * Atualiza os dados de um medicamento existente validando o perfil ativo na sessão.
     */
    public int atualizarMedicamento(Medicamento med) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return atualizarMedicamento(med, perfilAtivo);
    }

    /**
     * Atualiza os dados de um medicamento existente no SQLite com trava de segurança RBAC
     * e sincroniza automaticamente os alarmes no AlarmManager.
     * @throws AcessoNaoAutorizadoException se o perfil não puder editar medicamentos
     */
    public int atualizarMedicamento(Medicamento med, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeEditarMedicamento(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de atualizar medicamento por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Editar/Atualizar Medicamento");
        }

        if (med == null || med.getId() <= 0) {
            Log.w(TAG, "Tentativa de atualizar medicamento inválido ou sem ID.");
            return 0;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(DatabaseHelper.COL_MED_ID_IDOSO, med.getIdIdoso());
        cv.put(DatabaseHelper.COL_MED_NOME, med.getNomeMedicamento());
        cv.put(DatabaseHelper.COL_MED_DOSAGEM, med.getDosagem());
        cv.put(DatabaseHelper.COL_MED_ESTOQUE, med.getEstoque());
        cv.put(DatabaseHelper.COL_MED_TIPO, med.getTipo());
        cv.put(DatabaseHelper.COL_MED_HORARIO_INICIAL, med.getHorarioInicial());
        cv.put(DatabaseHelper.COL_MED_FREQUENCIA_HORAS, med.getFrequenciaHoras());
        cv.put(DatabaseHelper.COL_MED_OBSERVACAO, med.getObservacaoMedica());
        cv.put(DatabaseHelper.COL_MED_STATUS_ATIVO, med.getStatusAtivo());

        int linhasAfetadas = db.update(
                DatabaseHelper.TABLE_MEDICAMENTO,
                cv,
                DatabaseHelper.COL_MED_ID + " = ?",
                new String[]{String.valueOf(med.getId())}
        );

        if (linhasAfetadas > 0) {
            Log.d(TAG, "Medicamento atualizado no SQLite com sucesso: ID " + med.getId());

            // 1. Cancela o alarme com os parâmetros antigos no AlarmManager
            AlarmScheduler.cancelarAlarme(context, med.getId());

            // 2. Se a prescrição estiver ativa, reagenda imediatamente o alarme com os novos horários
            if (med.isAtivo()) {
                AlarmScheduler.agendarMedicamento(context, med);
                Log.d(TAG, "Alarme reagendado no AlarmManager para: " + med.getNomeMedicamento()
                        + " às " + med.getHorarioInicial() + " a cada " + med.getFrequenciaHoras() + "h");
            } else {
                Log.d(TAG, "Medicamento inativo. Alarme removido sem reagendamento.");
            }
        } else {
            Log.e(TAG, "Falha ao atualizar medicamento ID " + med.getId() + " no SQLite.");
        }

        return linhasAfetadas;
    }

    /**
     * Remove um medicamento do SQLite e cancela seu alarme com perfil da sessão.
     */
    public int excluirMedicamento(int idMedicamento) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return excluirMedicamento(idMedicamento, perfilAtivo);
    }

    /**
     * Remove um medicamento do SQLite com trava de segurança RBAC e cancela o alarme.
     * @throws AcessoNaoAutorizadoException se o perfil não puder excluir medicamentos
     */
    public int excluirMedicamento(int idMedicamento, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeExcluirMedicamento(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de excluir medicamento por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Excluir Medicamento");
        }

        if (idMedicamento <= 0) return 0;

        // Cancela o alarme nativo antes de excluir
        AlarmScheduler.cancelarAlarme(context, idMedicamento);
        Log.d(TAG, "Alarme cancelado para medicamento ID " + idMedicamento);

        return dbHelper.excluirMedicamento(idMedicamento);
    }

    /**
     * Busca um medicamento por seu ID.
     */
    public Medicamento buscarPorId(int idMedicamento) {
        return dbHelper.buscarMedicamentoPorId(idMedicamento);
    }

    /**
     * Lista todos os medicamentos prescritos para um idoso específico.
     */
    public List<Medicamento> listarPorIdoso(int idIdoso) {
        return dbHelper.listarMedicamentosPorIdoso(idIdoso);
    }

    /**
     * Retorna a lista calculada das doses do idoso para o dia com status de pontualidade.
     */
    public List<DoseAlertaDTO> buscarProximasDoses(int idIdoso) {
        return dbHelper.buscarProximasDoses(idIdoso);
    }

    /**
     * Retorna todos os alertas do dia de todos os residentes ativos.
     */
    public List<DoseAlertaDTO> buscarAlertasDoDia() {
        return dbHelper.buscarAlertasDoDia();
    }

    /**
     * Registra a confirmação de administração ou recusa de uma dose no histórico.
     */
    public long registrarAplicacao(HistoricoMedicacao historico) {
        PerfilUsuario perfilAtivo = SessaoManager.getPerfil(context);
        return registrarAplicacao(historico, perfilAtivo);
    }

    /**
     * Registra a confirmação de dose com validação RBAC explícita.
     */
    public long registrarAplicacao(HistoricoMedicacao historico, PerfilUsuario perfil) {
        if (!PermissaoUtils.podeConfirmarDose(perfil)) {
            Log.e(TAG, "Tentativa não autorizada de registrar aplicação por: " + perfil);
            throw new AcessoNaoAutorizadoException(perfil, "Confirmar Aplicação de Dose");
        }
        return dbHelper.registrarAplicacao(historico);
    }

    /**
     * Lista todos os medicamentos cadastrados na instituição (para controle de estoque e relatórios).
     */
    public List<Medicamento> listarTodosMedicamentos() {
        return dbHelper.listarTodosMedicamentos();
    }
}
