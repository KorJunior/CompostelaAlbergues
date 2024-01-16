package com.mycompany.basedatoscompostela;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.text.Text;

public class PrincipalController {

    Connection conexion;
    final String user = "root";
    final String pass = "docker";
    final String urlDriver = "com.mysql.cj.jdbc.Driver";
    final String urlConexion = "jdbc:mysql://localhost:3306/elcamino";

    @FXML
    private DatePicker fechaLlegada;
    @FXML
    private TextField dni;
    @FXML
    private ComboBox<String> albergue;
    @FXML
    private ComboBox<String> etapa;
    @FXML
    private RadioButton frances;
    @FXML
    private RadioButton aragones;
    @FXML
    private Text nombreUsuario;

    public void initialize() throws ClassNotFoundException, SQLException {
        ToggleGroup group = new ToggleGroup();

        frances.setToggleGroup(group);
        aragones.setToggleGroup(group);
        Class.forName(urlDriver);
        conexion = DriverManager.getConnection(urlConexion, user, pass);
        aragones.setSelected(true);

        enterdni();
        opcionesDeCamino();
        opcionBaseCamino();
        opcionAlbergue();
        crearProcedureInsertarReserva();

    }

    private void crearProcedureInsertarReserva() throws SQLException {
        StringBuilder query = new StringBuilder();
        Statement sentencia;

        query.append("DROP PROCEDURE IF EXISTS `InsertarReserva`");
        sentencia = conexion.createStatement();
        sentencia.execute(query.toString());

        query.setLength(0); // Limpiar el StringBuilder

        query.append("CREATE PROCEDURE `InsertarReserva`(IN _dni INT, IN _fecha DATE, IN _idalbergue varchar(5), OUT resultado BOOLEAN) ");
        query.append("BEGIN ");
        query.append("DECLARE reserva_count INT;");
        query.append("DECLARE plazas_totales INT;");
        query.append("DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN SET resultado = FALSE; END; ");
        query.append("SELECT COUNT(*) INTO reserva_count ");
        query.append("FROM RESERVA ");
        query.append("WHERE fecha = _fecha AND idalbergue = _idalbergue;");
        query.append("SELECT capacidad INTO plazas_totales ");
        query.append("FROM albergue ");
        query.append("WHERE idalbergue = _idalbergue;");
        query.append("IF plazas_totales > reserva_count THEN ");
        query.append("INSERT INTO RESERVA (dni, fecha, idalbergue) ");
        query.append("VALUES (_dni, _fecha, _idalbergue);");
        query.append("SET resultado = TRUE;");
        query.append("ELSE ");
        query.append("SET resultado = FALSE;");
        query.append("END IF;");
        query.append("END;");

        sentencia.execute(query.toString());

        sentencia.close();

    }

    @FXML
    private void limpiar(ActionEvent event) {
        fechaLlegada.setValue(null);
        dni.clear();
        albergue.getSelectionModel().clearSelection();
        etapa.getSelectionModel().clearSelection();
        frances.setSelected(false);
        aragones.setSelected(true);
    }

    @FXML
    private void reservar(ActionEvent event) throws SQLException {
        String dniStr, albergueSeleccionado, idAlbergue;
        String fechaLlegadaIns;
        LocalDate fecha;
        Boolean resultado;
        String auxt;
        dniStr = dni.getText().trim();
        fechaLlegadaIns = fechaLlegada.getEditor().getText();
        fecha = comprobarFecha(fechaLlegada.getValue());

        if (!compruebaDatos(dniStr, fechaLlegadaIns)) {
            return;
        }
        if (!estaDni(dniStr)) {
            return;
        }
        if (fecha == null) {
            auxt = "La fecha introducida es de dias anterior al\n" + "actual o no tiene formato valido";
            muestraError(auxt);
            return;
        }
        java.sql.Date sqlDate = java.sql.Date.valueOf(fechaLlegada.getValue());
        if (albergue.getValue() == null) {
            muestraError("Introduzca algun valor en el albergue");
            return;
        }
        albergueSeleccionado = albergue.getValue().trim().substring(0, albergue.getValue().lastIndexOf("-"));

        resultado = llamarProcesoInsercionReservas(Integer.parseInt(dniStr), sqlDate, albergueSeleccionado.trim());
        if (resultado) {
            muestraExito();
        } else {
            muestraError("No se puedo realizar la reserva");
        }
    }

    private boolean estaDni(String dni) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder();
        boolean existeDni;

        if (!isNumeric(dni)) {
            muestraError("El dni introducido no es un numero");
            return false;
        }

        sqlBuilder.append("Select dni from peregrino where dni = ?");
        String sql = sqlBuilder.toString();
        PreparedStatement sentencia = conexion.prepareStatement(sql);
        sentencia.setInt(1, Integer.parseInt(dni));

        ResultSet resultSet = sentencia.executeQuery();

        existeDni = resultSet.next();

        resultSet.close();
        sentencia.close();

        if (!existeDni) {
            muestraError("el DNI no esta registrado en la base de datos");
        }

        return existeDni;

    }

    private boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    private void muestraError(String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error al realizar la acción");
        alert.setContentText("Algo salió mal: " + mensaje);
        alert.showAndWait();
    }

    private void muestraExito() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Exito");
        alert.setHeaderText("Acción completada");
        alert.setContentText("La reserva se realizó con exito ");
        alert.showAndWait();
    }

    private boolean compruebaDatos(String dni, String fecha) {
        if (fecha.equalsIgnoreCase("")) {
            muestraError("Introduzca la fecha");
            return false;
        }

        if (dni == null) {
            muestraError("Introduzca el DNI");
            return false;
        }
        if (!dni.matches("\\d{4}")) {
            muestraError("Formato de DNI incorrecto");
            return false;
        }
        return true;

    }

    public static boolean fechaValida(String fecha) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate.parse(fecha, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public ArrayList<String> consultarEtapaCamino(String etapa) throws ClassNotFoundException, SQLException {
        ArrayList<String> resultados = new ArrayList<>();

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT e.numetapa, c1.nomciudad, c2.nomciudad ");
        sqlBuilder.append("FROM etapa e ");
        sqlBuilder.append("INNER JOIN ciudad c1 ON e.origen = c1.idciudad ");
        sqlBuilder.append("INNER JOIN ciudad c2 ON e.destino = c2.idciudad ");
        sqlBuilder.append("WHERE e.nomcamino LIKE ?;");

        String sql = sqlBuilder.toString();

        PreparedStatement sentencia = conexion.prepareStatement(sql);
        sentencia.setString(1, etapa);

        ResultSet resultSet = sentencia.executeQuery();

        while (resultSet.next()) {
            String numEtapa = resultSet.getString(1);
            String ciudadOrigen = resultSet.getString(2);
            String ciudadDestino = resultSet.getString(3);

            resultados.add(numEtapa + ": " + ciudadOrigen + "-" + ciudadDestino);
        }

        resultSet.close();
        sentencia.close();

        return resultados;
    }

    public ArrayList<String> consultarAlbergue(String etapa) throws ClassNotFoundException, SQLException {
        ArrayList<String> resultados = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder();
        String sql, idAlbergue, nomAlbergue, plazasDisp, ciudad;
        PreparedStatement sentencia;
        ResultSet resultSet;

        ciudad = etapa.substring(etapa.lastIndexOf("-") + 1);
        sqlBuilder.append("SELECT a.idalbergue, a.nomalbergue,a.capacidad ");
        sqlBuilder.append("FROM albergue a ");
        sqlBuilder.append("INNER JOIN ciudad c ");
        sqlBuilder.append("ON a.idciudad = c.idciudad where c.nomciudad LIKE ?;");

        sql = sqlBuilder.toString();
        sentencia = conexion.prepareStatement(sql);
        sentencia.setString(1, ciudad);
        resultSet = sentencia.executeQuery();

        while (resultSet.next()) {
            idAlbergue = resultSet.getString(1);
            nomAlbergue = resultSet.getString(2);
            plazasDisp = resultSet.getString(3);

            resultados.add(idAlbergue + " - " + nomAlbergue + ":" + plazasDisp);
        }

        resultSet.close();
        sentencia.close();

        return resultados;
    }
    //MySQL
    /*
            String consulta = "INSERT INTO tu_tabla (tu_columna1, tu_columna2) VALUES (?, ?)";
            PreparedStatement sentencia = conexion.prepareStatement(consulta);
            sentencia.setString(1, "tu_valor1");
            sentencia.setString(2, "tu_valor2");

            int filasInsertadas = sentencia.executeUpdate();

            System.out.println("Filas insertadas: " + filasInsertadas);
    */
    /*
            String consulta = "DELETE FROM tu_tabla WHERE tu_columna = ?";
            PreparedStatement sentencia = conexion.prepareStatement(consulta);
            sentencia.setString(1, "tu_valor");

            int filasEliminadas = sentencia.executeUpdate();

            System.out.println("Filas eliminadas: " + filasEliminadas);
    */
    /*
            String consulta = "UPDATE tu_tabla SET tu_columna1 = ? WHERE tu_columna2 = ?";
            PreparedStatement sentencia = conexion.prepareStatement(consulta);
            sentencia.setString(1, "nuevo_valor");
            sentencia.setString(2, "tu_valor");

            int filasActualizadas = sentencia.executeUpdate();

            System.out.println("Filas actualizadas: " + filasActualizadas);
    */
    /*
    switch (filasActualizadas) {
                case 0 -> {
                    System.out.println("SISTEMA: No se ha actualizado ninguna fila.");
                    return false;
                }
                case 1 ->
                    System.out.println("SISTEMA: Se ha actualizado " + filasActualizadas + " fila de la tabla.");
                default -> {
                    System.out.println("SISTEMA: Se han actualizado " + filasActualizadas + " filas de la tabla.");
                    return true;
                }
            }
    */
    
    //SQLite
    /*
    // Insertar
String consulta = "INSERT INTO tu_tabla (tu_columna1, tu_columna2) VALUES (?, ?)";
SQLiteStatement sentencia = conexion.compileStatement(consulta);
sentencia.bindString(1, "tu_valor1");
sentencia.bindString(2, "tu_valor2");

long filasInsertadas = sentencia.executeInsert();

System.out.println("Filas insertadas: " + filasInsertadas);

    */
    /*
    // Eliminar
String consulta = "DELETE FROM tu_tabla WHERE tu_columna = ?";
SQLiteStatement sentencia = conexion.compileStatement(consulta);
sentencia.bindString(1, "tu_valor");

int filasEliminadas = sentencia.executeUpdateDelete();

System.out.println("Filas eliminadas: " + filasEliminadas);

    */
    /*
    // Actualizar
String consulta = "UPDATE tu_tabla SET tu_columna1 = ? WHERE tu_columna2 = ?";
SQLiteStatement sentencia = conexion.compileStatement(consulta);
sentencia.bindString(1, "nuevo_valor");
sentencia.bindString(2, "tu_valor");

int filasActualizadas = sentencia.executeUpdateDelete();

System.out.println("Filas actualizadas: " + filasActualizadas);

    */
    /*
    // Crear una consulta para seleccionar datos en SQLite
String consultaSelect = "SELECT * FROM tu_tabla WHERE tu_columna = ?";
SQLiteStatement sentenciaSelect = conexion.compileStatement(consultaSelect);
sentenciaSelect.bindString(1, "tu_valor");

Cursor cursor = sentenciaSelect.executeQuery();

// Recorrer los resultados
while (cursor.moveToNext()) {
    String columna1 = cursor.getString(cursor.getColumnIndex("tu_columna1"));
    String columna2 = cursor.getString(cursor.getColumnIndex("tu_columna2"));

    System.out.println("Columna 1: " + columna1 + ", Columna 2: " + columna2);
}

cursor.close();

    */
    
    
    
    

    private void opcionesDeCamino() {
        aragones.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                try {
                    ArrayList<String> datos = consultarEtapaCamino("Aragones");
                    etapa.getItems().clear();
                    etapa.getItems().addAll(datos);
                    albergue.getItems().clear();
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(PrincipalController.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SQLException ex) {
                    Logger.getLogger(PrincipalController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

        frances.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {

                try {
                    ArrayList<String> datos = consultarEtapaCamino("Frances");
                    etapa.getItems().clear();
                    etapa.getItems().addAll(datos);
                    albergue.getItems().clear();
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(PrincipalController.class.getName()).log(Level.SEVERE, null, ex);
                } catch (SQLException ex) {
                    Logger.getLogger(PrincipalController.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        });
    }

    private void opcionBaseCamino() {
        if (aragones.isSelected()) {
            try {
                ArrayList<String> datos = consultarEtapaCamino("Aragones");
                etapa.getItems().clear();
                etapa.getItems().addAll(datos);
            } catch (ClassNotFoundException | SQLException ex) {
                Logger.getLogger(PrincipalController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void opcionAlbergue() {
        etapa.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                try {
                    ArrayList<String> albergues = consultarAlbergue(newValue);
                    albergue.getItems().clear();
                    albergue.getItems().addAll(albergues);
                } catch (ClassNotFoundException | SQLException ex) {
                    Logger.getLogger(PrincipalController.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    private boolean llamarProcesoInsercionReservas(int dni, Date fecha, String id) throws SQLException {
        String query = "{CALL InsertarReserva(?, ?, ?, ?)}";
        boolean resultado = false;
        CallableStatement sentencia = conexion.prepareCall(query);

        sentencia.setInt(1, dni);
        sentencia.setDate(2, fecha);
        sentencia.setString(3, id);
        sentencia.registerOutParameter(4, Types.BOOLEAN);

        sentencia.execute();

        resultado = sentencia.getBoolean(4);

        sentencia.close();

        return resultado;
    }

    private LocalDate comprobarFecha(LocalDate fechaLlegadaIns) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String fechaString;
        LocalDate fechaIntroducida, hoy;

        try {
            if (fechaLlegadaIns == null) {
                return null;
            }
            fechaString = fechaLlegadaIns.format(formatter);
            fechaIntroducida = LocalDate.parse(fechaString, formatter);
            hoy = LocalDate.now();

            if (!fechaIntroducida.isBefore(hoy)) {
                return fechaIntroducida;
            } else {
                return null;
            }
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void enterdni() {
        dni.setOnAction(event -> {
            try {
                String dniStr = dni.getText(), nombre;
                if (!estaDni(dniStr)) {
                    return;
                }
                nombre = consultarNombre(dniStr);

                nombreUsuario.setText(nombre);
            } catch (SQLException ex) {
                muestraError("DNI no posible");
            }
        });
    }

    private String consultarNombre(String dni) throws SQLException {
        String nombre = null;
        StringBuilder sqlBuilder = new StringBuilder();

        sqlBuilder.append("Select nombre from peregrino where dni = ?");
        String sql = sqlBuilder.toString();
        PreparedStatement sentencia = conexion.prepareStatement(sql);
        sentencia.setInt(1, Integer.parseInt(dni));
        ResultSet resultSet = sentencia.executeQuery();

        if (resultSet.next()) {
            nombre = resultSet.getString("nombre");
        }

        resultSet.close();
        sentencia.close();
        return nombre;
    }

}
