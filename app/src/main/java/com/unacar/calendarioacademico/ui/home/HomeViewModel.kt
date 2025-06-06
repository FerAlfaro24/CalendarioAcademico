package com.unacar.calendarioacademico.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.unacar.calendarioacademico.modelos.Materia
import com.unacar.calendarioacademico.utilidades.AdministradorFirebase

class HomeViewModel : ViewModel() {

    private val _nombre = MutableLiveData<String>()
    val nombre: LiveData<String> = _nombre

    private val _tipoUsuario = MutableLiveData<String>()
    val tipoUsuario: LiveData<String> = _tipoUsuario

    private val _materias = MutableLiveData<List<Materia>>()
    val materias: LiveData<List<Materia>> = _materias

    private val _cargando = MutableLiveData<Boolean>()
    val cargando: LiveData<Boolean> = _cargando

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var listenerMaterias: ListenerRegistration? = null
    private var listenerInscripciones: ListenerRegistration? = null

    init {
        cargarDatosUsuario()
    }

    private fun cargarDatosUsuario() {
        _cargando.value = true
        val usuario = FirebaseAuth.getInstance().currentUser

        if (usuario != null) {
            AdministradorFirebase.obtenerPerfilUsuario(usuario.uid).get()
                .addOnSuccessListener { documento ->
                    if (documento.exists()) {
                        _nombre.value = documento.getString("nombre") ?: "Usuario"
                        val tipoUsuario = documento.getString("tipoUsuario") ?: "estudiante"
                        _tipoUsuario.value = tipoUsuario

                        // Cargar materias según tipo de usuario
                        if (tipoUsuario == "profesor") {
                            cargarMateriasPorProfesor(usuario.uid)
                        } else {
                            cargarMateriasEstudiante(usuario.uid)
                        }
                    } else {
                        _error.value = "No se encontró el perfil del usuario"
                        _cargando.value = false
                    }
                }
                .addOnFailureListener {
                    _error.value = "Error al cargar perfil: ${it.message}"
                    _cargando.value = false
                }
        } else {
            _error.value = "Usuario no autenticado"
            _cargando.value = false
        }
    }

    private fun cargarMateriasPorProfesor(idProfesor: String) {
        // Detener listener anterior si existe
        listenerMaterias?.remove()

        // Crear nuevo listener en tiempo real
        listenerMaterias = AdministradorFirebase.escucharMateriasPorProfesor(idProfesor) { materias ->
            _materias.value = materias
            _cargando.value = false
        }
    }

    private fun cargarMateriasEstudiante(idEstudiante: String) {
        // Detener listeners anteriores si existen
        listenerInscripciones?.remove()

        // Crear nuevo listener en tiempo real para inscripciones
        listenerInscripciones = AdministradorFirebase.escucharMateriasEstudiante(idEstudiante) { idsMaterias ->
            if (idsMaterias.isEmpty()) {
                _materias.value = emptyList()
                _cargando.value = false
                return@escucharMateriasEstudiante
            }

            // Ahora obtener los detalles de cada materia
            val db = FirebaseFirestore.getInstance()
            val listaMaterias = mutableListOf<Materia>()
            var materiasCompletadas = 0

            for (idMateria in idsMaterias) {
                db.collection("materias").document(idMateria)
                    .get()
                    .addOnSuccessListener { documentSnapshot ->
                        materiasCompletadas++

                        val materia = documentSnapshot.toObject(Materia::class.java)
                        if (materia != null) {
                            // Asignar el ID del documento al objeto
                            materia.id = documentSnapshot.id
                            listaMaterias.add(materia)
                        }

                        // Si ya procesamos todas las materias, actualizamos el LiveData
                        if (materiasCompletadas == idsMaterias.size) {
                            _materias.value = listaMaterias
                            _cargando.value = false
                        }
                    }
                    .addOnFailureListener {
                        materiasCompletadas++

                        if (materiasCompletadas == idsMaterias.size) {
                            _materias.value = listaMaterias
                            _cargando.value = false
                        }
                    }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Limpiar listeners al destruir el ViewModel
        listenerMaterias?.remove()
        listenerInscripciones?.remove()
    }
}