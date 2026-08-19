package com.prathmesh.spendwise.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prathmesh.spendwise.userservice.UserService;
import com.prathmesh.spendwise.userservice.dto.request.UserRequest;
import com.prathmesh.spendwise.userservice.dto.response.UserResponse;
import com.prathmesh.spendwise.userservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(UserController.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;


    // --------------------------------------------------
    // CREATE
    // --------------------------------------------------

    @Test
    void createUser_shouldReturn201() throws Exception {

        UserRequest request = new UserRequest();
        request.setFirstName("Prathamesh");
        request.setLastName("Padavekar");
        request.setEmail("prathamesh@gmail.com");
        request.setPhone("9876543210");

        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setFirstName("Prathamesh");
        response.setLastName("Padavekar");
        response.setEmail("prathamesh@gmail.com");
        response.setPhone("9876543210");

        when(userService.createUser(any(UserRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Prathamesh"))
                .andExpect(jsonPath("$.email").value("prathamesh@gmail.com"));

        verify(userService).createUser(any(UserRequest.class));
    }


    // --------------------------------------------------
    // GET ALL
    // --------------------------------------------------

    @Test
    void getAllUsers_shouldReturn200() throws Exception {

        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setFirstName("Prathamesh");
        response.setLastName("Padavekar");
        response.setEmail("prathamesh@gmail.com");
        response.setPhone("9876543210");

        when(userService.getAllUsers())
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/v1/users")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstName").value("Prathamesh"));

        verify(userService).getAllUsers();
    }


    // --------------------------------------------------
    // GET BY ID
    // --------------------------------------------------

    @Test
    void getUserById_shouldReturn200() throws Exception {

        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setFirstName("Prathamesh");
        response.setLastName("Padavekar");
        response.setEmail("prathamesh@gmail.com");
        response.setPhone("9876543210");

        when(userService.getUserById(1L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/users/1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Prathamesh"))
                .andExpect(jsonPath("$.email").value("prathamesh@gmail.com"));

        verify(userService).getUserById(1L);
    }


    // --------------------------------------------------
    // UPDATE
    // --------------------------------------------------

    @Test
    void updateUser_shouldReturn200() throws Exception {

        UserRequest request = new UserRequest();
        request.setFirstName("Updated");
        request.setLastName("User");
        request.setEmail("updated@gmail.com");
        request.setPhone("9999999999");

        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setFirstName("Updated");
        response.setLastName("User");
        response.setEmail("updated@gmail.com");
        response.setPhone("9999999999");

        when(userService.updateUser(
                eq(1L),
                any(UserRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        put("/api/v1/users/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.email").value("updated@gmail.com"));

        verify(userService)
                .updateUser(eq(1L), any(UserRequest.class));
    }


    // --------------------------------------------------
    // DELETE
    // --------------------------------------------------

    @Test
    void deleteUser_shouldReturn204() throws Exception {

        doNothing()
                .when(userService)
                .deleteUser(1L);

        mockMvc.perform(
                        delete("/api/v1/users/1")
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userService).deleteUser(1L);
    }


    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    @Test
    void createUser_shouldReturn400WhenRequestIsInvalid() throws Exception {

        UserRequest request = new UserRequest();

        request.setFirstName("");
        request.setLastName("");
        request.setEmail("invalid-email");
        request.setPhone("123");

        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());

        verify(userService, never())
                .createUser(any(UserRequest.class));
    }

    @Test
    void createUser_shouldReturnBadRequestWhenValidationFails() throws Exception {

        UserRequest request = new UserRequest();
        request.setFirstName("");
        request.setLastName("");
        request.setEmail("invalid-email");
        request.setPhone("");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getUserById_shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {

        when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
