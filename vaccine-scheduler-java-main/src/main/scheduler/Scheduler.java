package scheduler;

import scheduler.db.ConnectionManager;
import scheduler.model.Caregiver;
import scheduler.model.Patient;
import scheduler.model.Vaccine;
import scheduler.util.Util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;

public class Scheduler {

    // objects to keep track of the currently logged-in user
    // Note: it is always true that at most one of currentCaregiver and currentPatient is not null
    //       since only one user can be logged-in at a time
    private static Caregiver currentCaregiver = null;
    private static Patient currentPatient = null;

    public static void main(String[] args) {
        // printing greetings text
        System.out.println();
        System.out.println("Welcome to the COVID-19 Vaccine Reservation Scheduling Application!");
        System.out.println("*** Please enter one of the following commands ***");
        System.out.println("> create_patient <username> <password>");  //TODO: implement create_patient (Part 1)
        System.out.println("> create_caregiver <username> <password>");
        System.out.println("> login_patient <username> <password>");  // TODO: implement login_patient (Part 1)
        System.out.println("> login_caregiver <username> <password>");
        System.out.println("> search_caregiver_schedule <date>");  // TODO: implement search_caregiver_schedule (Part 2)
        System.out.println("> reserve <date> <vaccine>");  // TODO: implement reserve (Part 2)
        System.out.println("> upload_availability <date>");
        System.out.println("> cancel <appointment_id>");  // TODO: implement cancel (extra credit)
        System.out.println("> add_doses <vaccine> <number>");
        System.out.println("> show_appointments");  // TODO: implement show_appointments (Part 2)
        System.out.println("> logout");  // TODO: implement logout (Part 2)
        System.out.println("> quit");
        System.out.println();

        // read input from user
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("> ");
            String response = "";
            try {
                response = r.readLine();
            } catch (IOException e) {
                System.out.println("Please try again!");
            }
            // split the user input by spaces
            String[] tokens = response.split(" ");
            // check if input exists
            if (tokens.length == 0) {
                System.out.println("Please try again!");
                continue;
            }
            // determine which operation to perform
            String operation = tokens[0];
            if (operation.equals("create_patient")) {
                createPatient(tokens);
            } else if (operation.equals("create_caregiver")) {
                createCaregiver(tokens);
            } else if (operation.equals("login_patient")) {
                loginPatient(tokens);
            } else if (operation.equals("login_caregiver")) {
                loginCaregiver(tokens);
            } else if (operation.equals("search_caregiver_schedule")) {
                searchCaregiverSchedule(tokens);
            } else if (operation.equals("reserve")) {
                reserve(tokens);
            } else if (operation.equals("upload_availability")) {
                uploadAvailability(tokens);
            } else if (operation.equals("cancel")) {
                cancel(tokens);
            } else if (operation.equals("add_doses")) {
                addDoses(tokens);
            } else if (operation.equals("show_appointments")) {
                showAppointments(tokens);
            } else if (operation.equals("logout")) {
                logout(tokens);
            } else if (operation.equals("quit")) {
                System.out.println("Bye!");
                return;
            } else {
                System.out.println("Invalid operation name!");
            }
        }
    }

    private static void createPatient(String[] tokens) {
        // create_patient <username> <password>
        if (tokens.length != 3) {
            System.out.println("Invalid command format.");
            return;
        }

        String username = tokens[1];
        String password = tokens[2];

        if (usernameExistsPatient(username)) {
            System.out.println("Username taken, try again!");
            return;
        }

        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);

        try {
            Patient patient = new Patient.PatientBuilder(username, salt, hash).build();
            patient.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsPatient(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Patients WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void createCaregiver(String[] tokens) {
        // create_caregiver <username> <password>
        // check 1: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Failed to create user.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];
        // check 2: check if the username has been taken already
        if (usernameExistsCaregiver(username)) {
            System.out.println("Username taken, try again!");
            return;
        }
        byte[] salt = Util.generateSalt();
        byte[] hash = Util.generateHash(password, salt);
        // create the caregiver
        try {
            Caregiver caregiver = new Caregiver.CaregiverBuilder(username, salt, hash).build(); 
            // save to caregiver information to our database
            caregiver.saveToDB();
            System.out.println("Created user " + username);
        } catch (SQLException e) {
            System.out.println("Failed to create user.");
            e.printStackTrace();
        }
    }

    private static boolean usernameExistsCaregiver(String username) {
        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        String selectUsername = "SELECT * FROM Caregivers WHERE Username = ?";
        try {
            PreparedStatement statement = con.prepareStatement(selectUsername);
            statement.setString(1, username);
            ResultSet resultSet = statement.executeQuery();
            // returns false if the cursor is not before the first record or if there are no rows in the ResultSet.
            return resultSet.isBeforeFirst();
        } catch (SQLException e) {
            System.out.println("Error occurred when checking username");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
        return true;
    }

    private static void loginPatient(String[] tokens) {
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }

        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }

        String username = tokens[1];
        String password = tokens[2];

        Patient patient = null;
        try {
            patient = new Patient.PatientGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }

        if (patient == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentPatient = patient;
        }
    }

    private static void loginCaregiver(String[] tokens) {
        // login_caregiver <username> <password>
        // check 1: if someone's already logged-in, they need to log out first
        if (currentCaregiver != null || currentPatient != null) {
            System.out.println("User already logged in.");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Login failed.");
            return;
        }
        String username = tokens[1];
        String password = tokens[2];

        Caregiver caregiver = null;
        try {
            caregiver = new Caregiver.CaregiverGetter(username, password).get();
        } catch (SQLException e) {
            System.out.println("Login failed.");
            e.printStackTrace();
        }
        // check if the login was successful
        if (caregiver == null) {
            System.out.println("Login failed.");
        } else {
            System.out.println("Logged in as: " + username);
            currentCaregiver = caregiver;
        }
    }

    private static void searchCaregiverSchedule(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }

        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }

        String date = tokens[1];

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        try {
            String query = "SELECT Username FROM Availabilities WHERE Date = ? ORDER BY Username";
            PreparedStatement statement = con.prepareStatement(query);
            statement.setDate(1, Date.valueOf(date));
            ResultSet caregivers = statement.executeQuery();

            while (caregivers.next()) {
                String caregiverUsername = caregivers.getString("Username");
                System.out.print(caregiverUsername + " ");

                String vaccineQuery = "SELECT Name, Doses FROM Vaccines WHERE Doses > 0";
                PreparedStatement vaccineStatement = con.prepareStatement(vaccineQuery);
                ResultSet vaccineResults = vaccineStatement.executeQuery();

                while (vaccineResults.next()) {
                    String vaccineName = vaccineResults.getString("Name");
                    int doses = vaccineResults.getInt("Doses");
                    System.out.print(vaccineName + " " + doses + " ");
                }
                System.out.println();
            }
        } catch (SQLException e) {
            System.out.println("Please try again!");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void reserve(String[] tokens) {
        if (currentPatient == null) {
            System.out.println(currentCaregiver == null ? "Please login first!" : "Please login as a patient!");
            return;
        }

        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }

        String date = tokens[1];
        String vaccineName = tokens[2];

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        try {
            String checkDosesQuery = "SELECT Doses FROM Vaccines WHERE Name = ?";
            PreparedStatement dosesStmt = con.prepareStatement(checkDosesQuery);
            dosesStmt.setString(1, vaccineName);
            ResultSet dosesResult = dosesStmt.executeQuery();
            if (!dosesResult.next() || dosesResult.getInt("Doses") <= 0) {
                System.out.println("Not enough available doses!");
                return;
            }

            String findCaregiverQuery = "SELECT Username FROM Availabilities WHERE Date = ? ORDER BY Username LIMIT 1";
            PreparedStatement caregiverStmt = con.prepareStatement(findCaregiverQuery);
            caregiverStmt.setDate(1, Date.valueOf(date));
            ResultSet caregiverResult = caregiverStmt.executeQuery();
            if (!caregiverResult.next()) {
                System.out.println("No Caregiver is available!");
                return;
            }

            String caregiverUsername = caregiverResult.getString("Username");

            String createAppointmentQuery = "INSERT INTO Appointments (Date, Vaccine, PatientUsername, CaregiverUsername) VALUES (?, ?, ?, ?)";
            PreparedStatement appointmentStmt = con.prepareStatement(createAppointmentQuery, Statement.RETURN_GENERATED_KEYS);
            appointmentStmt.setDate(1, Date.valueOf(date));
            appointmentStmt.setString(2, vaccineName);
            appointmentStmt.setString(3, currentPatient.getUsername());
            appointmentStmt.setString(4, caregiverUsername);
            int affectedRows = appointmentStmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating appointment failed, no rows affected.");
            }

            String updateVaccineQuery = "UPDATE Vaccines SET Doses = Doses - 1 WHERE Name = ?";
            PreparedStatement updateVaccineStmt = con.prepareStatement(updateVaccineQuery);
            updateVaccineStmt.setString(1, vaccineName);
            updateVaccineStmt.executeUpdate();

            try (ResultSet generatedKeys = appointmentStmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long appointmentId = generatedKeys.getLong(1);
                    System.out.println("Appointment ID: " + appointmentId + ", Caregiver username: " + caregiverUsername);
                } else {
                    throw new SQLException("Creating appointment failed, no ID obtained.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Please try again!");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void uploadAvailability(String[] tokens) {
        // upload_availability <date>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 2 to include all information (with the operation name)
        if (tokens.length != 2) {
            System.out.println("Please try again!");
            return;
        }
        String date = tokens[1];
        try {
            Date d = Date.valueOf(date);
            currentCaregiver.uploadAvailability(d);
            System.out.println("Availability uploaded!");
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a valid date!");
        } catch (SQLException e) {
            System.out.println("Error occurred when uploading availability");
            e.printStackTrace();
        }
    }

    private static void cancel(String[] tokens) {
        // TODO: Extra credit
    }

    private static void addDoses(String[] tokens) {
        // add_doses <vaccine> <number>
        // check 1: check if the current logged-in user is a caregiver
        if (currentCaregiver == null) {
            System.out.println("Please login as a caregiver first!");
            return;
        }
        // check 2: the length for tokens need to be exactly 3 to include all information (with the operation name)
        if (tokens.length != 3) {
            System.out.println("Please try again!");
            return;
        }
        String vaccineName = tokens[1];
        int doses = Integer.parseInt(tokens[2]);
        Vaccine vaccine = null;
        try {
            vaccine = new Vaccine.VaccineGetter(vaccineName).get();
        } catch (SQLException e) {
            System.out.println("Error occurred when adding doses");
            e.printStackTrace();
        }
        // check 3: if getter returns null, it means that we need to create the vaccine and insert it into the Vaccines
        //          table
        if (vaccine == null) {
            try {
                vaccine = new Vaccine.VaccineBuilder(vaccineName, doses).build();
                vaccine.saveToDB();
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        } else {
            // if the vaccine is not null, meaning that the vaccine already exists in our table
            try {
                vaccine.increaseAvailableDoses(doses);
            } catch (SQLException e) {
                System.out.println("Error occurred when adding doses");
                e.printStackTrace();
            }
        }
        System.out.println("Doses updated!");
    }

    private static void showAppointments(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first!");
            return;
        }

        ConnectionManager cm = new ConnectionManager();
        Connection con = cm.createConnection();

        try {
            PreparedStatement statement;
            String query;

            if (currentCaregiver != null) {
                query = "SELECT AppointmentID, Vaccine, Date, PatientUsername FROM Appointments WHERE CaregiverUsername = ? ORDER BY AppointmentID";
                statement = con.prepareStatement(query);
                statement.setString(1, currentCaregiver.getUsername());
            } else {
                query = "SELECT AppointmentID, Vaccine, Date, CaregiverUsername FROM Appointments WHERE PatientUsername = ? ORDER BY AppointmentID";
                statement = con.prepareStatement(query);
                statement.setString(1, currentPatient.getUsername());
            }

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                long appointmentId = resultSet.getLong("AppointmentID");
                String vaccineName = resultSet.getString("Vaccine");
                Date date = resultSet.getDate("Date");
                String otherUsername = resultSet.getString(currentCaregiver != null ? "PatientUsername" : "CaregiverUsername");

                System.out.println(appointmentId + " " + vaccineName + " " + date + " " + otherUsername);
            }
        } catch (SQLException e) {
            System.out.println("Please try again!");
            e.printStackTrace();
        } finally {
            cm.closeConnection();
        }
    }

    private static void logout(String[] tokens) {
        if (currentCaregiver == null && currentPatient == null) {
            System.out.println("Please login first.");
            return;
        }

        currentCaregiver = null;
        currentPatient = null;
        System.out.println("Successfully logged out!");
    }
}
