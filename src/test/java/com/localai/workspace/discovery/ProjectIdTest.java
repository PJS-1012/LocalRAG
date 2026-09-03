package com.localai.workspace.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectIdTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void createsPortableWorkspaceRelativeIdentifier() {
        Path projectRoot = workspaceRoot.resolve("Room_Reservation/RoomReservation");

        assertThat(ProjectId.from(workspaceRoot, projectRoot))
                .isEqualTo("Room_Reservation/RoomReservation");
    }

    @Test
    void resolvesForwardAndWindowsSeparators() {
        Path expected = workspaceRoot.resolve("Room_Reservation/RoomReservation").normalize();

        assertThat(ProjectId.resolve(workspaceRoot, "Room_Reservation/RoomReservation"))
                .contains(expected);
        assertThat(ProjectId.resolve(workspaceRoot, "Room_Reservation\\RoomReservation"))
                .contains(expected);
    }

    @Test
    void rejectsAbsoluteTraversalAndMalformedIdentifiers() {
        assertThat(ProjectId.resolve(workspaceRoot, "../outside")).isEmpty();
        assertThat(ProjectId.resolve(workspaceRoot, "Room_Reservation/../Local_Ai_Work")).isEmpty();
        assertThat(ProjectId.resolve(workspaceRoot, "C:/workspace/Local_Ai_Work")).isEmpty();
        assertThat(ProjectId.resolve(workspaceRoot, "/workspace/Local_Ai_Work")).isEmpty();
        assertThat(ProjectId.resolve(workspaceRoot, "Room_Reservation//RoomReservation")).isEmpty();
        assertThat(ProjectId.resolve(workspaceRoot, " ")).isEmpty();
    }
}
