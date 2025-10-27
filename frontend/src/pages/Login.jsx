import { useState } from "react";
import axios from "axios";
import { API_URL } from "../config";

export default function Login() {
  const [usuario, setUsuario] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  const handleLogin = async (e) => {
    e.preventDefault();
    try {
      const res = await axios.post(`${API_URL}/login`, { usuario, password });
      localStorage.setItem("token", res.data.token);
      localStorage.setItem("rol", res.data.rol);
      window.location.href = res.data.rol === "admin" ? "/admin" : "/solicitudes";
    } catch {
      setError("Credenciales inválidas");
    }
  };

  return (
    <div style={{ maxWidth: 400, margin: "40px auto", textAlign: "center" }}>
      <h2>Iniciar sesión</h2>
      <form onSubmit={handleLogin}>
        <input placeholder="Usuario" value={usuario} onChange={(e) => setUsuario(e.target.value)} required /><br/>
        <input type="password" placeholder="Contraseña" value={password} onChange={(e) => setPassword(e.target.value)} required /><br/>
        <button type="submit">Entrar</button>
      </form>
      {error && <p style={{ color: "red" }}>{error}</p>}
    </div>
  );
}
