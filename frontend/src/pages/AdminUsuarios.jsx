import { useState, useEffect } from "react";
import axios from "axios";
import { Link } from "react-router-dom";
import { API_URL } from "../config";

export default function AdminUsuarios() {
  const [usuarios, setUsuarios] = useState([]);
  const [nuevoUsuario, setNuevoUsuario] = useState("");
  const [password, setPassword] = useState("");
  const [nuevoRol, setNuevoRol] = useState("empleado");
  const [mensaje, setMensaje] = useState("");
  const [eliminandoId, setEliminandoId] = useState(null);
  const [busqueda, setBusqueda] = useState("");

  const token = localStorage.getItem("token");

  const cargarUsuarios = async () => {
    try {
      const res = await axios.get(`${API_URL}/usuarios`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      setUsuarios(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { cargarUsuarios(); }, []);

  const crearUsuario = async (e) => {
    e.preventDefault();
    try {
      await axios.post(`${API_URL}/usuarios`,
        { usuario: nuevoUsuario, password, nuevoRol },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setMensaje("✅ Usuario creado correctamente");
      setNuevoUsuario(""); setPassword(""); setNuevoRol("empleado");
      cargarUsuarios();
    } catch (err) {
      setMensaje("❌ Error al crear usuario");
    }
  };

  const usuariosFiltrados = usuarios.filter(
    (u) =>
      u.usuario.toLowerCase().includes(busqueda.toLowerCase()) ||
      u.rol.toLowerCase().includes(busqueda.toLowerCase())
  );

  return (
    <div style={{ maxWidth: 700, margin: "40px auto", textAlign: "center" }}>
      <h2>Gestión de Usuarios</h2>
      <Link to="/admin"><button>Volver al Panel</button></Link>

      <form onSubmit={crearUsuario} style={{ marginTop: 20 }}>
        <input type="text" placeholder="Nuevo usuario" value={nuevoUsuario} onChange={e => setNuevoUsuario(e.target.value)} required /><br/>
        <input type="password" placeholder="Contraseña" value={password} onChange={e => setPassword(e.target.value)} required /><br/>
        <select value={nuevoRol} onChange={e => setNuevoRol(e.target.value)}>
          <option value="empleado">Empleado</option>
          <option value="admin">Administrador</option>
        </select><br/>
        <button type="submit" style={{ marginTop: 10 }}>Crear usuario</button>
      </form>
      {mensaje && <p style={{ marginTop: 10 }}>{mensaje}</p>}

      <div style={{ marginTop: 30 }}>
        <input
          type="text"
          placeholder="Buscar por usuario o rol..."
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          style={{ padding: 8, width: "60%", marginBottom: 15 }}
        />
      </div>

      <h3>Usuarios existentes</h3>
      <table border="1" cellPadding="8" style={{ margin: "0 auto" }}>
        <thead><tr><th>ID</th><th>Usuario</th><th>Rol</th><th>Acciones</th></tr></thead>
        <tbody>
          {usuariosFiltrados.map((u) => (
            <tr key={u.id}>
              <td>{u.id}</td><td>{u.usuario}</td><td>{u.rol}</td>
              <td>
                {u.rol !== "admin" ? (
                  <button
                    disabled={eliminandoId === u.id}
                    onClick={async () => {
                      if (confirm(`¿Eliminar al usuario ${u.usuario}?`)) {
                        setEliminandoId(u.id);
                        try {
                          await axios.delete(`${API_URL}/usuarios/${u.id}`, {
                            headers: { Authorization: `Bearer ${token}` },
                          });
                          setMensaje("✅ Usuario eliminado correctamente");
                          cargarUsuarios();
                        } catch (err) {
                          console.error(err);
                          setMensaje("❌ Error al eliminar el usuario");
                        } finally {
                          setEliminandoId(null);
                        }
                      }
                    }}
                  >
                    {eliminandoId === u.id ? "Eliminando..." : "Eliminar"}
                  </button>
                ) : (<i>Protegido</i>)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
