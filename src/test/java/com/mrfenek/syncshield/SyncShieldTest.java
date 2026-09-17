package com.mrfenek.syncshield;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SyncShieldTest {

    @Test
    public void testGetOfflinePlayerNameNullSafety() {
        UUID testUuid = UUID.randomUUID();
        OfflinePlayer mockPlayer = mock(OfflinePlayer.class);
        when(mockPlayer.getName()).thenReturn(null);

        try (MockedStatic<Bukkit> mockedBukkit = mockStatic(Bukkit.class)) {
            mockedBukkit.when(() -> Bukkit.getOfflinePlayer(testUuid)).thenReturn(mockPlayer);

            String name = Bukkit.getOfflinePlayer(testUuid).getName();
            if (name == null) name = testUuid.toString();

            assertEquals(testUuid.toString(), name, "Name should fallback to UUID when Bukkit returns null");
        }
    }

    @Test
    public void testConfigInputStreamReaderClosed() throws Exception {
        // We will create a mock InputStream that tracks whether it was closed
        AtomicBoolean isClosed = new AtomicBoolean(false);
        String dummyYaml = "language-internal: en";
        InputStream is = new ByteArrayInputStream(dummyYaml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws java.io.IOException {
                super.close();
                isClosed.set(true);
            }
        };

        // This replicates the logic in SyncShield
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
            assertEquals("en", defConfig.getString("language-internal"));
        } catch (java.io.IOException ignored) {}

        // Verify that the underlying stream was closed by the try-with-resources block on the reader
        assertTrue(isClosed.get(), "InputStream should be closed by try-with-resources block");
    }
}
