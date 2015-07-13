/**
 * This file Copyright (c) 2011-2015 Magnolia International
 * Ltd.  (http://www.magnolia-cms.com). All rights reserved.
 *
 *
 * This file is dual-licensed under both the Magnolia
 * Network Agreement and the GNU General Public License.
 * You may elect to use one or the other of these licenses.
 *
 * This file is distributed in the hope that it will be
 * useful, but AS-IS and WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE, TITLE, or NONINFRINGEMENT.
 * Redistribution, except as permitted by whichever of the GPL
 * or MNA you select, is prohibited.
 *
 * 1. For the GPL license (GPL), you can redistribute and/or
 * modify this file under the terms of the GNU General
 * Public License, Version 3, as published by the Free Software
 * Foundation.  You should have received a copy of the GNU
 * General Public License, Version 3 along with this program;
 * if not, write to the Free Software Foundation, Inc., 51
 * Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * 2. For the Magnolia Network Agreement (MNA), this file
 * and the accompanying materials are made available under the
 * terms of the MNA which accompanies this distribution, and
 * is available at http://www.magnolia-cms.com/mna.html
 *
 * Any modifications to this file must keep this entire header
 * intact.
 *
 */
package info.magnolia.services.rest;

import info.magnolia.context.MgnlContext;
import info.magnolia.jcr.util.PropertyUtil;
import info.magnolia.rest.AbstractEndpoint;

import java.util.List;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.RepositoryException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;


/**
 * Subscriber REST endpoint.
 * Duplicate an existing subscriber and change its name according to the value passed in the query
 * The duplicated subscriber can be configured in the end point definition.
 *
 * @param <D> tag
 */
@Path("/subscribers/v1")
public class SubscribersEndpoint<D extends ConfiguredSubscribersEndpointDefinition> extends AbstractEndpoint<D> {

    private static final String BASE_PATH = "/server/activation/subscribers/";
    private static final String WORKSPACE = "config";


    @Inject
    protected SubscribersEndpoint(D endpointDefinition) {
        super(endpointDefinition);
    }


    @PUT
    @Path("/{subscriberName}")
    public Response createOrUpdateSubscriber(@PathParam("subscriberName") String subscriberName, @QueryParam("url") String url) throws RepositoryException {

        final Session session = MgnlContext.getJCRSession(WORKSPACE);

        String parentAbsPath = BASE_PATH;
        String templateAbsPath = BASE_PATH + this.getEndpointDefinition().getSubscriberTemplateName();


        if (!session.nodeExists(parentAbsPath)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!session.nodeExists(templateAbsPath)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        final Node parentNode = session.getNode(parentAbsPath);

        if (parentNode.hasNode(subscriberName)) {

            Node subscriberNode = parentNode.getNode(subscriberName);

            PropertyUtil.setProperty(subscriberNode, "URL", url);
            activateSubscriver(subscriberNode);
        } else {

            final Node templateNode = session.getNode(templateAbsPath);

            deactivateSubscriber(templateNode);

            final Node clonedNode = clone(templateNode, parentNode, subscriberName);

            activateSubscriver(clonedNode);

            PropertyUtil.setProperty(parentNode.getNode(subscriberName), "URL", url);
        }

        session.save();

        return Response.ok().build();
    }

    @DELETE
    @Path("/")
    public Response deactivateAllSubscribers() throws RepositoryException {
        final Session session = MgnlContext.getJCRSession(WORKSPACE);

        NodeIterator subscribers = session.getNode(BASE_PATH).getNodes();
        while(subscribers.hasNext()) {
            deactivateSubscriber(subscribers.nextNode());
        }
        session.save();
        return Response.ok().build();
    }


    private void deactivateSubscriber(Node node) {
        try {
            node.setProperty("active", "false");
        } catch (RepositoryException e) {
            //todo exception
            throw new RuntimeException(e);
        }
    }

    private void activateSubscriver(Node node) {
        try {
            node.setProperty("active", "true");
        } catch (RepositoryException e) {
            //todo exception
            throw new RuntimeException(e);
        }
    }


    private Node clone(Node node, Node parent, String overrideName) {

        // create the new node under the parent
        Node clone = null;
        try {
            clone = parent.addNode(overrideName, node.getPrimaryNodeType().getName());


            // copy properties
            PropertyIterator properties = node.getProperties();

            while (properties.hasNext()) {
                Property property = properties.nextProperty();
                if (!property.getName().startsWith("jcr:") && !property.getName().startsWith("mgnl:")) {
                    PropertyUtil.setProperty(clone, property.getName(), property.getValue());
                }
            }

            // copy subnodes
            NodeIterator children = node.getNodes();

            while (children.hasNext()) {
                Node child = children.nextNode();
                clone(child, clone, child.getName());
            }

            return clone;

        } catch (RepositoryException e) {
            //todo exception
            throw new RuntimeException(e);
        }
    }

}
