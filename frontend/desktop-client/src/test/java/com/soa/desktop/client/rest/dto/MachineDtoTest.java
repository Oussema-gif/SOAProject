package com.soa.desktop.client.rest.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MachineDtoTest {

    @Test
    void mapsTheMaintenanceApiMachineIdentifierToTheClientId() throws Exception {
        MachineDto machine = new ObjectMapper().readValue("""
                {
                  "machineId": "MACHINE-001",
                  "name": "Press line 01",
                  "status": "RUNNING",
                  "location": "Assembly"
                }
                """, MachineDto.class);

        assertEquals("MACHINE-001", machine.id());
    }
}
