import express from "express";
import cors from "cors";
import authRoutes from "./routes/auth.js";
import solicitudRoutes from "./routes/solicitudes.js";
import "./models/initDB.js";

const app = express();
app.use(cors());
app.use(express.json());

app.use("/api", authRoutes);
app.use("/api/solicitudes", solicitudRoutes);

export default app;

// Para correr localmente, puedes descomentar:
// if (process.env.NODE_ENV !== "production") {
//   const PORT = process.env.PORT || 4000;
//   app.listen(PORT, () => console.log(`✅ Backend local en http://localhost:${PORT}`));
// }
