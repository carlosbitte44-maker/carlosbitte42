package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String = "",
    val empresa: String = "",
    val transportadora: String = "",
    val rua: String = "",
    val numero: String = "",
    val complemento: String = "",
    val condominio: String = "",
    val bloco: String = "",
    val apartamento: String = "",
    val bairro: String = "",
    val cidade: String = "",
    val estado: String = "",
    val cep: String = "",
    val telefone: String = "",
    val pedido: String = "",
    val sku: String = "",
    val produto: String = "",
    val codigoBarras: String = "",
    val qrCode: String = "",
    val peso: String = "",
    val volume: String = "",
    val imagemEtiqueta: String? = null,
    val dataCadastro: Long = System.currentTimeMillis(),
    val usuario: String = "Operador",
    val status: String = "Pendente",
    val observacoes: String = "",
    val confidence: Float = 1.0f,
    val carrierLayout: String = "Desconhecido",
    val readTimeMs: Long = 0,
    val addressValidated: Boolean = false
)
