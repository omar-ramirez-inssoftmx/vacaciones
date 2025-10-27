import express from "express";
import cors from "cors";
import usuariosRoutes from "./routes/usuarios.js";
import solicitudesRoutes from "./routes/solicitudes.js";

const app = express();
app.use(cors());
app.use(express.json());

// Rutas
app.use("/api/usuarios", usuariosRoutes);
app.use("/api/solicitudes", solicitudesRoutes);

// Ruta raíz (opcional)
app.get("/", (req, res) => {
  res.send("Backend activo 🚀");
});

// Para correr localmente, puedes descomentar:
// if (process.env.NODE_ENV !== "production") {
//   const PORT = process.env.PORT || 4000;
//   app.listen(PORT, () => console.log(`✅ Backend local en http://localhost:${PORT}`));
// }
export default app;


