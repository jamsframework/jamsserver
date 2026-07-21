/*
 * UserFacadeREST.java
 * Created on 01.03.2014, 21:37:11
 *
 * This file is part of JAMS
 * Copyright (C) FSU Jena
 *
 * JAMS is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * JAMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JAMS. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package jams.server.service;

import jams.server.entities.User;
import jams.server.entities.Users;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 *
 * @author Christian Fischer <christian.fischer.2@uni-jena.de>
 */
@Stateless
@Path("user")
public class UserFacadeREST extends AbstractFacade<User> {

    @PersistenceContext(unitName = "jams-serverPU")
    private EntityManager em;

    public UserFacadeREST() {
        super(User.class);
    }

    @PUT
    @Path("create")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response create(User entity, @Context HttpServletRequest req) {
        if (isAdmin(req)) {
            if (!findByName(entity.getLogin()).isEmpty())
                return Response.status(Response.Status.CONFLICT).build();

            entity.setPassword(PasswordHasher.hash(
                    entity.getPassword() == null ? "" : entity.getPassword()));
            super.create(entity);
            if (entity.getId() != null)
                return Response.ok(scrub(entity)).build();
            else{
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            }
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    @PUT
    @Path("{id}")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response edit(@PathParam("id") Integer id, User entity, @Context HttpServletRequest req) {
        User user = getCurrentUser(req);

        if (user == null || (user.getId() != id && user.getAdmin() == 0)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Get unchanged version of entity
        User originalEntity = getEntityManager().find(User.class, id);

        // Prevent creating new user
        if (originalEntity == null) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Prevent changing the login to another user’s login
        if (!isLoginAvailable(entity.getLogin(), id)) {
            return Response.status(Response.Status.CONFLICT).build();
        }

        originalEntity.setEmail(entity.getEmail());
        originalEntity.setLogin(entity.getLogin());
        originalEntity.setName(entity.getName());

        // Only update admin role if user is actually admin
        if (user.getAdmin() > 0) {
            originalEntity.setAdmin(entity.getAdmin());
        }

        // Only update password if a new one was supplied; store it hashed.
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            originalEntity.setPassword(PasswordHasher.hash(entity.getPassword()));
        }

        super.edit(originalEntity);
        return Response.ok(scrub(originalEntity)).build();
    }

    @DELETE
    @Path("{id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response remove(@PathParam("id") Integer id, @Context HttpServletRequest req) {
        if (isAdmin(req)) {
            User o = super.find(id);
            if (o == null)
                return null;
            super.remove(o);
            return Response.ok(scrub(o)).build();
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    @GET
    @Path("{id}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response find(@PathParam("id") Integer id, @Context HttpServletRequest req) {
        if (isAdmin(req)) {
            return Response.ok(scrub(super.find(id))).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @GET
    @Path("all")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response findAll(@Context HttpServletRequest req) {
        if (isAdmin(req)) {
            return Response.ok(scrub(new Users(super.findAll()))).build();
        } else {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
    }

    @GET
    @Path("{from}/{to}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response findRange(@PathParam("from") Integer from, @PathParam("to") Integer to, @Context HttpServletRequest req) {
        if (!isAdmin(req)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }
        return Response.ok(scrub(new Users(super.findRange(new int[]{from, to})))).build();
    }

    @GET
    @Path("count")
    @Produces({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
    public Response countREST(@Context HttpServletRequest req) {
        return Response.ok(String.valueOf(super.count())).build();
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @POST
    @Path("login")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response login(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @Context HttpServletRequest req) {
        HttpSession session = req.getSession(true);

        // Credentials arrive as an "Authorization: Basic base64(login:password)"
        // header, so they never appear in the URL or the server access log.
        String[] creds = decodeBasic(authorization);
        if (creds != null) {
            User user = findUserByLogin(creds[0]);
            if (user != null && PasswordHasher.verify(creds[1], user.getPassword())) {
                session.setAttribute("userid", user.getId());
                session.setAttribute("userlogin", user.getLogin());
                return Response.ok(scrub(user)).build();
            }
        }
        session.setAttribute("userid", "-1");
        session.setAttribute("userlogin", "");
        return Response.status(Status.FORBIDDEN).build();
    }

    @GET
    @Path("logout")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response logout(@Context HttpServletRequest req) {
        HttpSession session = req.getSession(true);
        session.setAttribute("userid", "-1");
        session.setAttribute("userlogin", "");
        return Response.ok().build();
    }

    @GET
    @Path("isConnected")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response isConnected(@Context HttpServletRequest req) {
        if (!isLoggedIn(req)) {
            return Response.ok(Boolean.toString(false)).build();
        }
        return Response.ok(Boolean.toString(true)).build();
    }

    /**
     * Returns a detached copy of the user without the password. A copy is used on
     * purpose: mutating the managed entity would also null the password in
     * EclipseLink's shared cache and break subsequent logins.
     */
    private static User scrub(User u) {
        if (u == null) {
            return null;
        }
        User copy = new User();
        copy.setId(u.getId());
        copy.setLogin(u.getLogin());
        copy.setName(u.getName());
        copy.setEmail(u.getEmail());
        copy.setAdmin(u.getAdmin());
        // password intentionally left null
        return copy;
    }

    private static Users scrub(Users users) {
        if (users == null || users.getUsers() == null) {
            return users;
        }
        List<User> copies = new ArrayList<>(users.getUsers().size());
        for (User u : users.getUsers()) {
            copies.add(scrub(u));
        }
        return new Users(copies);
    }

    private static String[] decodeBasic(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String creds = new String(
                    Base64.getDecoder().decode(authorization.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int idx = creds.indexOf(':');
            if (idx < 0) {
                return null;
            }
            return new String[]{creds.substring(0, idx), creds.substring(idx + 1)};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private User findUserByLogin(String login) {
        List<User> list = em.createQuery(
                "SELECT u FROM User u WHERE u.login = :login", User.class)
                .setParameter("login", login)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    private List findByName(String login) {
        return em.createQuery(
                "SELECT u FROM User u WHERE u.login LIKE :login")
                .setParameter("login", login)
                .getResultList();
    }

    private boolean isLoginAvailable(String login, Integer id) {
        return em.createQuery(
                "SELECT u FROM User u WHERE u.login LIKE :login AND u.id != :id")
                .setParameter("id", id)
                .setParameter("login", login)
                .getResultList()
                .isEmpty();
    }
}
