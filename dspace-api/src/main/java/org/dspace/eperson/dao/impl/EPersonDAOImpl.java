/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.eperson.dao.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.MetadataField;
import org.dspace.core.AbstractHibernateDSODAO;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.dao.EPersonDAO;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.criterion.Restrictions;

import java.sql.SQLException;
import java.util.*;

/**
 * Hibernate implementation of the Database Access Object interface class for the EPerson object.
 * This class is responsible for all database calls for the EPerson object and is autowired by spring
 * This class should never be accessed directly.
 *
 * @author kevinvandevelde at atmire.com
 */
public class EPersonDAOImpl extends AbstractHibernateDSODAO<EPerson> implements EPersonDAO
{
    protected EPersonDAOImpl()
    {
        super();
    }

    @Override
    public EPerson findByEmail(Context context, String email) throws SQLException
    {
        // All email addresses are stored as lowercase, so ensure that the email address is lowercased for the lookup
        Criteria criteria = createCriteria(context, EPerson.class);
        criteria.add(Restrictions.eq("email", email.toLowerCase()));

        criteria.setCacheable(true);
        return uniqueResult(criteria);
    }


    @Override
    public EPerson findByNetid(Context context, String netid) throws SQLException
    {
        Criteria criteria = createCriteria(context, EPerson.class);
        criteria.add(Restrictions.eq("netid", netid));

        criteria.setCacheable(true);
        return uniqueResult(criteria);
    }

    /**
     * Create a query that joins the eperson table with the metadatavalue table
     * and checks if an eperson exists with the specified guid and userType.
     *
     * @param context
     *  The DSpace database context.
     *
     * @param guid
     *  The guid to query for.
     *
     * @param userType
     *  The userType to query for.
     *
     * @param queryFields
     *  List of MetadataField objects.
     *
     * @return eperson or null
     * @throws SQLException if database error
     */
    @Override
    public EPerson findByGuidAndUserType(Context context, String guid, String userType, List<MetadataField> queryFields)
            throws SQLException {
        Query query = createQuery(context,
                "SELECT eperson FROM EPerson as eperson " +
                        "left join eperson.metadata eperson_guid " +
                        "WITH eperson_guid.metadataField.id = :eperson_guid " +
                        "left join eperson.metadata eperson_userType " +
                        "WITH eperson_userType.metadataField.id = :eperson_userType " +
                        "WHERE  eperson_guid.value = :guidValue AND eperson_userType.value = :userTypeValue");
        query.setParameter("guidValue", guid);
        query.setParameter("userTypeValue", userType);

        for (MetadataField metadataField : queryFields) {
            query.setParameter(metadataField.toString(), metadataField.getID());
        }

        return singleResult(query);
    }

    @Override
    public List<EPerson> search(Context context, String query, List<MetadataField> queryFields, List<MetadataField> sortFields, int offset, int limit) throws SQLException
    {
        String queryString = "SELECT " + EPerson.class.getSimpleName().toLowerCase() + " FROM EPerson as " + EPerson.class.getSimpleName().toLowerCase() + " ";
        if(query != null) query= "%"+query.toLowerCase()+"%";
        Query hibernateQuery = getSearchQuery(context, queryString, query, queryFields, sortFields, null);

        if(0 <= offset)
        {
            hibernateQuery.setFirstResult(offset);
        }
        if(0 <= limit)
        {
            hibernateQuery.setMaxResults(limit);
        }
        return list(hibernateQuery);
    }

    @Override
    public int searchResultCount(Context context, String query, List<MetadataField> queryFields) throws SQLException
    {
        String queryString = "SELECT count(*) FROM EPerson as " + EPerson.class.getSimpleName().toLowerCase();
        Query hibernateQuery = getSearchQuery(context, queryString, query, queryFields, ListUtils.EMPTY_LIST, null);

        return count(hibernateQuery);
    }

    @Override
    public List<EPerson> findAll(Context context, MetadataField metadataSortField, String sortField, MetadataField userTypeField) throws SQLException {
        String queryString = "SELECT " + EPerson.class.getSimpleName().toLowerCase() + " FROM EPerson as " + EPerson.class.getSimpleName().toLowerCase();

        List<MetadataField> queryFields = ListUtils.EMPTY_LIST;
        if (userTypeField != null) {
            queryFields = Collections.singletonList(userTypeField);
        }
        List<MetadataField> sortFields = ListUtils.EMPTY_LIST;

        if(metadataSortField!=null){
            sortFields =  Collections.singletonList(metadataSortField);
        }

        Query query = getSearchQuery(context, queryString, null, queryFields, sortFields, sortField);

        return list(query);

    }

    @Override
    public List<EPerson> findByGroups(Context context, Set<Group> groups) throws SQLException {
        Query query = createQuery(context,
                "SELECT DISTINCT e FROM EPerson e " +
                        "JOIN e.groups g " +
                        "WHERE g.id IN (:idList) ");

        List<UUID> idList = new ArrayList<>(groups.size());
        for (Group group : groups) {
            idList.add(group.getID());
        }

        query.setParameterList("idList", idList);

        return list(query);
    }

    @Override
    public List<EPerson> findWithPasswordWithoutDigestAlgorithm(Context context) throws SQLException {
        Criteria criteria = createCriteria(context, EPerson.class);
        criteria.add(Restrictions.and(
                Restrictions.isNotNull("password"),
                Restrictions.isNull("digestAlgorithm")
        ));
        return list(criteria);
    }

    @Override
    public List<EPerson> findNotActiveSince(Context context, Date date) throws SQLException {
        Criteria criteria = createCriteria(context, EPerson.class);
        criteria.add(Restrictions.le("lastActive", date));
        return list(criteria);
    }

    protected Query getSearchQuery(Context context, String queryString, String queryParam, List<MetadataField> queryFields, List<MetadataField> sortFields, String sortField) throws SQLException {

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append(queryString);
        Set<MetadataField> metadataFieldsToJoin = new LinkedHashSet<>();
        metadataFieldsToJoin.addAll(queryFields);
        metadataFieldsToJoin.addAll(sortFields);

        if(!CollectionUtils.isEmpty(metadataFieldsToJoin)) {
            addMetadataLeftJoin(queryBuilder, EPerson.class.getSimpleName().toLowerCase(), metadataFieldsToJoin);
        }
        if(queryParam != null) {
            addMetadataValueWhereQuery(queryBuilder, queryFields, "like", EPerson.class.getSimpleName().toLowerCase() + ".email like :queryParam");
        }

        // If MetadataField for userType is provided, add where clause
        for (MetadataField metadataField : queryFields) {
            if (metadataField.getElement().equals("userType")) {
                queryBuilder.append(" WHERE eperson_userType.value = :userTypeValue");
            }
        }
        if(!CollectionUtils.isEmpty(sortFields)) {
            addMetadataSortQuery(queryBuilder, sortFields, Collections.singletonList(sortField));
        }

        Query query = createQuery(context, queryBuilder.toString());
        if(StringUtils.isNotBlank(queryParam)) {
            query.setParameter("queryParam", "%"+queryParam.toLowerCase()+"%");
        }
        for (MetadataField metadataField : metadataFieldsToJoin) {
            query.setParameter(metadataField.toString(), metadataField.getID());
            // If MetadataField for userType is provided, add value
            if (metadataField.getElement().equals("userType")) {
                query.setParameter("userTypeValue", "Saml2In:NYC Employees");
            }
        }

        return query;
    }

    @Override
    public List<EPerson> findAllSubscribers(Context context) throws SQLException {
        return list(createQuery(context, "SELECT DISTINCT e from Subscription s join s.ePerson e"));
    }

    @Override
    public int countRows(Context context) throws SQLException {
        return count(createQuery(context, "SELECT count(*) FROM EPerson"));
    }
}
