package com.orders.query.rebuild;

import com.orders.api.error.GlobalExceptionHandler;
import com.orders.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectionRebuildController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ProjectionRebuildControllerWebTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectionRebuilderService rebuilder;

    @Test
    void rebuildAllowsAdminAccess() throws Exception {
        mockMvc.perform(post("/admin/projections/rebuild")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk())
                .andExpect(content().string("Projection rebuild complete"));

        verify(rebuilder).rebuild();
    }

    @Test
    void rebuildRejectsUnauthenticated() throws Exception {
        mockMvc.perform(post("/admin/projections/rebuild"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rebuildRejectsNonAdminUser() throws Exception {
        mockMvc.perform(post("/admin/projections/rebuild")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orders"))))
                .andExpect(status().isForbidden());
    }
}
