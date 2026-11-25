package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.IlpiRoomDatabase
import com.example.data.local.entity.IdosoEntity
import com.example.data.local.entity.RegistroMedicoEntity
import com.example.data.repository.IlpiRepository
import com.example.model.Idoso
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IlpiRoomDatabaseTest {

    private lateinit var context: Context
    private lateinit var inMemoryDb: IlpiRoomDatabase
    private lateinit var repository: IlpiRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        IlpiRoomDatabase.resetInstanceForTesting()
        context.deleteDatabase(IlpiRoomDatabase.DATABASE_NAME)

        inMemoryDb = Room.inMemoryDatabaseBuilder(
            context,
            IlpiRoomDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = IlpiRepository(
            inMemoryDb.idosoDao(),
            inMemoryDb.registroMedicoDao()
        )
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
        IlpiRoomDatabase.resetInstanceForTesting()
        context.deleteDatabase(IlpiRoomDatabase.DATABASE_NAME)
    }

    @Test
    fun `inserir atualizar buscar e converter IdosoEntity com sucesso`() = runTest {
        val idoso = IdosoEntity(
            nome = "Dona Maria Aparecida",
            dataNascimento = "10/05/1940",
            cpf = "111.222.333-44",
            tipoSanguineo = "AB+",
            alergias = "Sulfa",
            restricoesMedicas = "Diabetes tipo 2",
            leitoQuarto = "Quarto 05 - Leito A",
            nomeFamiliar = "João Pedro",
            grauParentesco = "Filho",
            contatoEmergencia = "(51) 99988-7766"
        )

        val novoId = repository.inserirIdoso(idoso)
        assertTrue(novoId > 0L)

        val carregado = repository.observarIdosoPorId(novoId).first()
        assertNotNull(carregado)
        assertEquals("Dona Maria Aparecida", carregado?.nome)
        assertEquals("Quarto 05 - Leito A", carregado?.leitoQuarto)

        val modelConvertido: Idoso = carregado!!.toModel()
        assertEquals(novoId.toInt(), modelConvertido.id)
        assertEquals("Dona Maria Aparecida", modelConvertido.nome)
        assertEquals("111.222.333-44", modelConvertido.cpf)

        val reconvertido = IdosoEntity.fromModel(modelConvertido)
        assertEquals(carregado.nome, reconvertido.nome)
        assertEquals(carregado.alergias, reconvertido.alergias)

        val linhasAtualizadas = repository.atualizarIdoso(
            carregado.copy(alergias = "Sulfa e Dipirona", leitoQuarto = "Quarto 06 - Leito B")
        )
        assertEquals(1, linhasAtualizadas)

        val encontrados = repository.buscarIdososPorTermo("Dipirona").first()
        assertEquals(1, encontrados.size)
        assertEquals("Quarto 06 - Leito B", encontrados.first().leitoQuarto)
    }

    @Test
    fun `registros medicos vinculados devem responder a consultas por tipo alerta e exclusao em cascata`() = runTest {
        val idosoId = repository.inserirIdoso(
            IdosoEntity(
                nome = "Seu Geraldo Nunes",
                dataNascimento = "02/03/1937",
                tipoSanguineo = "O-",
                alergias = "Nenhuma",
                restricoesMedicas = "Cardiopatia isquêmica"
            )
        )

        val idSinais = repository.inserirRegistroMedico(
            RegistroMedicoEntity(
                idosoId = idosoId,
                tipoRegistro = RegistroMedicoEntity.TIPO_SINAIS_VITAIS,
                titulo = "Controle Pressórico",
                descricaoClinica = "Pressão arterial estável após repouso.",
                pressaoArterial = "120/75 mmHg",
                temperaturaCelsius = 36.6,
                glicemiaMgDl = 104,
                saturacaoO2 = 98,
                profissionalResponsavel = "Enf. Paula Costa",
                crmCoren = "COREN-RS 219044",
                alertaCritico = false
            )
        )

        val idIntercorrencia = repository.inserirRegistroMedico(
            RegistroMedicoEntity(
                idosoId = idosoId,
                tipoRegistro = RegistroMedicoEntity.TIPO_INTERCORRENCIA,
                titulo = "Pico Hipertensivo Noturno",
                descricaoClinica = "Elevação pressórica súbita; medicado conforme prescrição médica.",
                pressaoArterial = "170/100 mmHg",
                profissionalResponsavel = "Dr. Marcos Rezende",
                crmCoren = "CRM-RS 44120",
                alertaCritico = true
            )
        )

        assertTrue(idSinais > 0L && idIntercorrencia > 0L)

        val todosDoIdoso = repository.observarRegistrosMedicosDoIdoso(idosoId).first()
        assertEquals(2, todosDoIdoso.size)

        val somenteCriticos = repository.alertasClinicosCriticos.first()
        assertEquals(1, somenteCriticos.size)
        assertEquals("Pico Hipertensivo Noturno", somenteCriticos.first().titulo)

        val somenteSinais = repository.observarRegistrosMedicosPorTipo(
            idosoId,
            RegistroMedicoEntity.TIPO_SINAIS_VITAIS
        ).first()
        assertEquals(1, somenteSinais.size)
        assertEquals(104, somenteSinais.first().glicemiaMgDl)

        val relacao = repository.observarIdosoComRegistros(idosoId).first()
        assertNotNull(relacao)
        assertEquals("Seu Geraldo Nunes", relacao?.idoso?.nome)
        assertEquals(2, relacao?.registrosMedicos?.size)

        // Excluir o residente deve remover automaticamente seus registros médicos via CASCADE
        val excluidos = repository.excluirIdosoPorId(idosoId)
        assertEquals(1, excluidos)
        assertNull(repository.observarIdosoPorId(idosoId).first())
        assertTrue(repository.observarRegistrosMedicosDoIdoso(idosoId).first().isEmpty())
    }

    @Test
    fun `popularDadosIniciaisSeVazio deve semear residentes e registros medicos apenas uma vez`() = runTest {
        assertEquals(0, repository.totalIdososAtivos.first())

        val inseriuNaPrimeira = repository.popularDadosIniciaisSeVazio()
        assertTrue(inseriuNaPrimeira)
        assertEquals(2, repository.totalIdososAtivos.first())
        assertEquals(2, repository.todosRegistrosMedicos.first().size)

        val inseriuNaSegunda = repository.popularDadosIniciaisSeVazio()
        assertFalse(inseriuNaSegunda)
        assertEquals(2, repository.totalIdososAtivos.first())
    }

    @Test
    fun `instancia singleton em disco deve persistir registros localmente no dispositivo`() = runTest {
        val dbDisco = IlpiRoomDatabase.getInstance(context)
        val repoDisco = IlpiRepository.create(context)

        val id = repoDisco.inserirIdoso(
            IdosoEntity(
                nome = "Dona Nair Fagundes",
                dataNascimento = "19/09/1943",
                tipoSanguineo = "B+"
            )
        )
        assertTrue(id > 0L)
        assertEquals(1, repoDisco.totalIdososAtivos.first())
        assertTrue(context.getDatabasePath(IlpiRoomDatabase.DATABASE_NAME).exists())
        assertNotNull(dbDisco.openHelper.writableDatabase)
    }
}
