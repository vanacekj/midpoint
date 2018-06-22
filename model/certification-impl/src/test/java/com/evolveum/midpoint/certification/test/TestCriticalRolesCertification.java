/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.certification.test;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import java.io.File;
import java.util.*;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.CLOSED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.IN_REMEDIATION;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationResponseType.ACCEPT;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationResponseType.NOT_DECIDED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationResponseType.NO_RESPONSE;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationResponseType.REVOKE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * Tests itemSelectionExpression and useSubjectManager.
 *
 * @author mederly
 */
@ContextConfiguration(locations = {"classpath:ctx-certification-test-main.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class TestCriticalRolesCertification extends AbstractCertificationTest {

    private static final File CERT_DEF_FILE = new File(COMMON_DIR, "certification-of-critical-roles.xml");

    private AccessCertificationDefinitionType certificationDefinition;

    private String campaignOid;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);
        assignRole(USER_JACK_OID, ROLE_CTO_OID);
        userJack = getObjectViaRepo(UserType.class, USER_JACK_OID).asObjectable();
    }

    @Test
    public void test010CreateCampaign() throws Exception {
        final String TEST_NAME = "test010CreateCampaign";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        certificationDefinition = repoAddObjectFromFile(CERT_DEF_FILE,
                AccessCertificationDefinitionType.class, result).asObjectable();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationCampaignType campaign =
                certificationManager.createCampaign(certificationDefinition.getOid(), task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        assertNotNull("Created campaign is null", campaign);

        campaignOid = campaign.getOid();

        campaign = getCampaignWithCases(campaignOid);
        display("campaign", campaign);
        assertSanityAfterCampaignCreate(campaign, certificationDefinition);
    }

    /*
Expected cases, reviewers and decisions/outcomes:

CEO = 00000000-d34d-b33f-f00d-000000000001
COO = 00000000-d34d-b33f-f00d-000000000002

Stage1: oneAcceptAccepts, default: accept, stop on: revoke          (manager)

Case                        Stage1
================================================
elaine->CEO                 none (A) -> A
guybrush->COO               cheese: A -> A (in test100)
administrator->COO          none (A) -> A
administrator->CEO          none (A) -> A
jack->CEO                   none (A) -> A
jack->CTO                   none (A) -> A
     */

    @Test
    public void test020OpenFirstStage() throws Exception {
        final String TEST_NAME = "test020OpenFirstStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.openNextStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 1", campaign);
        assertSanityAfterCampaignStart(campaign, certificationDefinition, 6);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("unexpected # of cases", 6, caseList.size());
        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        checkCaseAssignmentSanity(elaineCeoCase, userElaine);
        checkCaseAssignmentSanity(guybrushCooCase, userGuybrush);
        checkCaseAssignmentSanity(administratorCeoCase, userAdministrator);
        checkCaseAssignmentSanity(administratorCooCase, userAdministrator);
        checkCaseAssignmentSanity(jackCeoCase, userJack);
        checkCaseAssignmentSanity(jackCtoCase, userJack);

        assertCaseReviewers(elaineCeoCase, ACCEPT, 1, emptyList());
        assertCaseReviewers(guybrushCooCase, NO_RESPONSE, 1, singletonList(USER_CHEESE_OID));
        assertCaseReviewers(administratorCooCase, ACCEPT, 1, emptyList());
        assertCaseReviewers(administratorCeoCase, ACCEPT, 1, emptyList());
        assertCaseReviewers(jackCeoCase, ACCEPT, 1, emptyList());
        assertCaseReviewers(jackCtoCase, ACCEPT, 1, emptyList());

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, null);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, ACCEPT, ACCEPT, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, ACCEPT, ACCEPT, null);

        assertPercentComplete(campaign, 83, 83, 0);
    }

    @Test
    public void test100RecordDecisions1() throws Exception {
        final String TEST_NAME = "test100RecordDecisions1";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);

        assertEquals("unexpected # of cases", 6, caseList.size());
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);

        recordDecision(campaignOid, guybrushCooCase, ACCEPT, null, USER_CHEESE_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);

        assertEquals("unexpected # of cases", 6, caseList.size());
        guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);

        assertSingleDecision(guybrushCooCase, ACCEPT, null, 1, 1, USER_CHEESE_OID, ACCEPT, false);

        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, ACCEPT, ACCEPT, null);

        assertPercentComplete(campaignOid, 100, 100, 100);
        assertCasesCount(campaignOid, 6);
    }

    @Test
    public void test150CloseFirstStage() throws Exception {
        final String TEST_NAME = "test150CloseFirstStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.closeCurrentStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 1", campaign);

        assertSanityAfterStageClose(campaign, certificationDefinition, 1);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("unexpected # of cases", 6, caseList.size());

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 1);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, ACCEPT, ACCEPT, 1);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, ACCEPT, ACCEPT, 1);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 1);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 1);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, ACCEPT, ACCEPT, 1);

        assertPercentComplete(campaignOid, 100, 100, 100);
        assertCasesCount(campaignOid, 6);
    }

    @Test
    public void test200OpenSecondStage() throws Exception {
        final String TEST_NAME = "test200OpenSecondStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.openNextStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 2", campaign);
        assertSanityAfterStageOpen(campaign, certificationDefinition, 2);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("Wrong number of certification cases", 6, caseList.size());
        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        /*
Stage2: allMustAccept, default: accept, advance on: accept          (target owner)

Case                        Stage1              Stage2
=============================================================
elaine->CEO                 none (A) -> A       elaine
guybrush->COO               cheese: A -> A      admin
administrator->COO          none (A) -> A       admin
administrator->CEO          none (A) -> A       elaine
jack->CEO                   none (A) -> A       elaine
jack->CTO                   none (A) -> A       none (A) -> A
         */

        assertCaseReviewers(elaineCeoCase, NO_RESPONSE, 2, singletonList(USER_ELAINE_OID));
        assertCaseReviewers(guybrushCooCase, NO_RESPONSE, 2, singletonList(USER_ADMINISTRATOR_OID));
        assertCaseReviewers(administratorCooCase, NO_RESPONSE, 2, singletonList(USER_ADMINISTRATOR_OID));
        assertCaseReviewers(administratorCeoCase, NO_RESPONSE, 2, singletonList(USER_ELAINE_OID));
        assertCaseReviewers(jackCeoCase, NO_RESPONSE, 2, singletonList(USER_ELAINE_OID));
        assertCaseReviewers(jackCtoCase, ACCEPT, 2, emptyList());

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, ACCEPT, ACCEPT, null);

        assertPercentComplete(campaignOid, 17, 17, 0);
        assertCasesCount(campaignOid, 6);
    }

    @Test
    public void test220StatisticsAllStages() throws Exception {
        final String TEST_NAME = "test220StatisticsAllStages";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationCasesStatisticsType stat =
                certificationManager.getCampaignStatistics(campaignOid, false, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("statistics", stat.asPrismContainerValue());
        assertEquals(1, stat.getMarkedAsAccept());
        assertEquals(0, stat.getMarkedAsRevoke());
        assertEquals(0, stat.getMarkedAsRevokeAndRemedied());
        assertEquals(0, stat.getMarkedAsReduce());
        assertEquals(0, stat.getMarkedAsReduceAndRemedied());
        assertEquals(0, stat.getMarkedAsNotDecide());
        assertEquals(5, stat.getWithoutResponse());
    }

    @Test
    public void test250RecordDecisionsSecondStage() throws Exception {
        final String TEST_NAME = "test250RecordDecisionsSecondStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);

/*
Stage2: allMustAccept, default: accept, advance on: accept          (target owner)

Overall: allMustAccept

owners: CEO: elaine, COO: administrator, CTO: none

Case                        Stage1              Stage2
=================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A

*/

        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);

        recordDecision(campaignOid, elaineCeoCase, ACCEPT, null, USER_ELAINE_OID, task, result);
        recordDecision(campaignOid, guybrushCooCase, REVOKE, "no", USER_ADMINISTRATOR_OID, task, result);
        recordDecision(campaignOid, administratorCooCase, ACCEPT, "ok", USER_ADMINISTRATOR_OID, task, result);
        recordDecision(campaignOid, administratorCeoCase, ACCEPT, null, USER_ELAINE_OID, task, result);
        // jackCeo: no response
        // jackCto: no reviewers

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 2", campaign);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);

        elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        assertWorkItemsCount(elaineCeoCase, 1);
        assertWorkItemsCount(guybrushCooCase, 2);
        assertWorkItemsCount(administratorCooCase, 1);
        assertWorkItemsCount(administratorCeoCase, 1);
        assertWorkItemsCount(jackCeoCase, 1);
        assertWorkItemsCount(jackCtoCase, 0);

        assertSingleDecision(elaineCeoCase, ACCEPT, null, 2, 1, USER_ELAINE_OID, ACCEPT, false);
        assertSingleDecision(guybrushCooCase, REVOKE, "no", 2, 1, USER_ADMINISTRATOR_OID, REVOKE, false);
        assertSingleDecision(administratorCooCase, ACCEPT, "ok", 2, 1, USER_ADMINISTRATOR_OID, ACCEPT, false);
        assertSingleDecision(administratorCeoCase, ACCEPT, null, 2, 1, USER_ELAINE_OID, ACCEPT, false);
        assertNoDecision(jackCeoCase, 2, 1, NO_RESPONSE, false);
        assertNoDecision(jackCtoCase, 2, 1, ACCEPT, false);

        assertPercentComplete(campaignOid, 83, 83, 80);
    }

    @Test
    public void test260Statistics() throws Exception {
        final String TEST_NAME = "test260Statistics";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationCasesStatisticsType stat =
                certificationManager.getCampaignStatistics(campaignOid, true, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("statistics", stat.asPrismContainerValue());
        assertEquals(4, stat.getMarkedAsAccept());
        assertEquals(1, stat.getMarkedAsRevoke());
        assertEquals(0, stat.getMarkedAsRevokeAndRemedied());
        assertEquals(0, stat.getMarkedAsReduce());
        assertEquals(0, stat.getMarkedAsReduceAndRemedied());
        assertEquals(0, stat.getMarkedAsNotDecide());
        assertEquals(1, stat.getWithoutResponse());
    }

    @Test
    public void test290CloseSecondStage() throws Exception {
        final String TEST_NAME = "test290CloseSecondStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.closeCurrentStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign after closing stage 2", campaign);
        assertSanityAfterStageClose(campaign, certificationDefinition, 2);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("wrong # of cases", 6, caseList.size());

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 2);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, REVOKE, REVOKE, 2);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, ACCEPT, ACCEPT, 2);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 2);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, 2);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, ACCEPT, ACCEPT, 2);

        assertPercentComplete(campaignOid, 83, 83, 80);
    }

    @Test
    public void test300OpenThirdStage() throws Exception {
        final String TEST_NAME = "test300OpenThirdStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.openNextStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 3", campaign);
        assertSanityAfterStageOpen(campaign, certificationDefinition, 3);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("Wrong number of certification cases", 6, caseList.size());
        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        /*
Stage3: oneDenyDenies, stop on: not decided

Overall: allMustAccept

owners: CEO: elaine, COO: administrator, CTO: none

Case                        Stage1              Stage2                           Stage3
=====================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine,administrator
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine,administrator
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine,administrator
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine,administrator
         */

        assertCaseReviewers(elaineCeoCase, NO_RESPONSE, 3, asList(USER_ELAINE_OID, USER_ADMINISTRATOR_OID));
        assertCaseReviewers(guybrushCooCase, REVOKE, 2, singletonList(USER_ADMINISTRATOR_OID));
        assertCaseReviewers(administratorCooCase, NO_RESPONSE, 3, asList(USER_ELAINE_OID, USER_ADMINISTRATOR_OID));
        assertCaseReviewers(administratorCeoCase, NO_RESPONSE, 3, asList(USER_ELAINE_OID, USER_ADMINISTRATOR_OID));
        assertCaseReviewers(jackCeoCase, NO_RESPONSE, 2, singletonList(USER_ELAINE_OID));
        assertCaseReviewers(jackCtoCase, NO_RESPONSE, 3, asList(USER_ELAINE_OID, USER_ADMINISTRATOR_OID));

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, REVOKE, REVOKE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, NO_RESPONSE, NO_RESPONSE, null);

        assertPercentComplete(campaignOid, 33, 17, 0);
    }

    @Test
    public void test330RecordDecisionsThirdStage() throws Exception {
        final String TEST_NAME = "test330RecordDecisionsThirdStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);

/*
Case                        Stage1              Stage2                           Stage3
==================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR

*/

        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase;
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase;
        AccessCertificationCaseType jackCtoCase;

        recordDecision(campaignOid, elaineCeoCase, NOT_DECIDED, null, USER_ADMINISTRATOR_OID, task, result);
        recordDecision(campaignOid, administratorCooCase, ACCEPT, null, USER_ELAINE_OID, task, result);
        recordDecision(campaignOid, administratorCeoCase, NO_RESPONSE, null, USER_ELAINE_OID, task, result);
        recordDecision(campaignOid, administratorCeoCase, NO_RESPONSE, null, USER_ADMINISTRATOR_OID, task, result);
        // no response for jackCto

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 3", campaign);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);

        elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        assertWorkItemsCount(elaineCeoCase, 3);
        assertWorkItemsCount(guybrushCooCase, 2);
        assertWorkItemsCount(administratorCooCase, 3);
        assertWorkItemsCount(administratorCeoCase, 3);
        assertWorkItemsCount(jackCeoCase, 1);
        assertWorkItemsCount(jackCtoCase, 2);

        assertReviewerDecision(elaineCeoCase, NOT_DECIDED, null, 3, 1, USER_ADMINISTRATOR_OID, NOT_DECIDED, false);
        assertNoDecision(guybrushCooCase, 3, 1, REVOKE, false);
        assertReviewerDecision(administratorCooCase, ACCEPT, null, 3, 1, USER_ELAINE_OID, ACCEPT, false);
        assertReviewerDecision(administratorCooCase, null, null, 3, 1, USER_ADMINISTRATOR_OID, ACCEPT, false);
        assertReviewerDecision(administratorCeoCase, null, null, 3, 1, USER_ELAINE_OID, NO_RESPONSE, false);
        assertReviewerDecision(administratorCeoCase, null, null, 3, 1, USER_ADMINISTRATOR_OID, NO_RESPONSE, false);
        assertNoDecision(jackCeoCase, 3, 1, NO_RESPONSE, false);
        assertReviewerDecision(jackCtoCase, null, null, 3, 1, USER_ELAINE_OID, NO_RESPONSE, false);
        assertReviewerDecision(jackCtoCase, null, null, 3, 1, USER_ADMINISTRATOR_OID, NO_RESPONSE, false);

        /*
Case                        Stage1              Stage2                           Stage3
==================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR

*/

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, NOT_DECIDED, NOT_DECIDED, null);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, REVOKE, REVOKE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, ACCEPT, ACCEPT, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, NO_RESPONSE, NO_RESPONSE, null);

        assertPercentComplete(campaignOid, 33, 33, 25);
    }

    @Test
    public void test390CloseThirdStage() throws Exception {
        final String TEST_NAME = "test390CloseThirdStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.closeCurrentStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign after closing stage 3", campaign);
        assertSanityAfterStageClose(campaign, certificationDefinition, 3);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("wrong # of cases", 6, caseList.size());

        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

                /*
Case                        Stage1              Stage2                           Stage3
==================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR

*/

        assertCaseHistoricOutcomes(elaineCeoCase, ACCEPT, ACCEPT, NOT_DECIDED);
        assertCaseHistoricOutcomes(guybrushCooCase, ACCEPT, REVOKE);
        assertCaseHistoricOutcomes(administratorCooCase, ACCEPT, ACCEPT, ACCEPT);
        assertCaseHistoricOutcomes(administratorCeoCase, ACCEPT, ACCEPT, NO_RESPONSE);
        assertCaseHistoricOutcomes(jackCeoCase, ACCEPT, NO_RESPONSE);
        assertCaseHistoricOutcomes(jackCtoCase, ACCEPT, ACCEPT, NO_RESPONSE);

        assertPercentComplete(campaignOid, 33, 33, 25);
    }

    @Test
    public void test400OpenFourthStage() throws Exception {
        final String TEST_NAME = "test400OpenFourthStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.openNextStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 4", campaign);
        assertSanityAfterStageOpen(campaign, certificationDefinition, 4);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("Wrong number of certification cases", 6, caseList.size());
        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        /*
Stage4: allMustAccept

Overall: allMustAccept

owners: CEO: elaine, COO: administrator, CTO: none

Case                        Stage1              Stage2                           Stage3                                            Stage4
===============================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A    cheese
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR   cheese
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR   cheese
         */

        assertCaseReviewers(elaineCeoCase, NOT_DECIDED, 3, asList(USER_ELAINE_OID, USER_ADMINISTRATOR_OID));
        assertCaseReviewers(guybrushCooCase, REVOKE, 2, singletonList(USER_ADMINISTRATOR_OID));
        assertCaseReviewers(administratorCooCase, NO_RESPONSE, 4, singletonList(USER_CHEESE_OID));
        assertCaseReviewers(administratorCeoCase, NO_RESPONSE, 4, singletonList(USER_CHEESE_OID));
        assertCaseReviewers(jackCeoCase, NO_RESPONSE, 2, singletonList(USER_ELAINE_OID));
        assertCaseReviewers(jackCtoCase, NO_RESPONSE, 4, singletonList(USER_CHEESE_OID));

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, NOT_DECIDED, NOT_DECIDED, null);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, REVOKE, REVOKE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, NO_RESPONSE, NO_RESPONSE, null);

        assertPercentComplete(campaignOid, 50, 17, 0);
    }

    @Test
    public void test430RecordDecisionsFourthStage() throws Exception {
        final String TEST_NAME = "test430RecordDecisionsFourthStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);

/*
Stage4: allMustAccept

Overall: allMustAccept

Case                        Stage1              Stage2                           Stage3                                            Stage4
===============================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A    cheese:A -> A | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR   cheese:A -> A | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR   cheese:NR -> NR | NR

*/

        AccessCertificationCaseType elaineCeoCase;
        AccessCertificationCaseType guybrushCooCase;
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase;
        AccessCertificationCaseType jackCtoCase;

        recordDecision(campaignOid, administratorCooCase, ACCEPT, null, USER_CHEESE_OID, task, result);
        recordDecision(campaignOid, administratorCeoCase, ACCEPT, null, USER_CHEESE_OID, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 4", campaign);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);

        elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        /*
Stage4: allMustAccept

Overall: allMustAccept

Case                        Stage1              Stage2                           Stage3                                            Stage4
===============================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A    cheese:A -> A | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR   cheese:A -> A | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR   cheese:NR -> NR | NR
*/

        assertWorkItemsCount(elaineCeoCase, 3);
        assertWorkItemsCount(guybrushCooCase, 2);
        assertWorkItemsCount(administratorCooCase, 4);
        assertWorkItemsCount(administratorCeoCase, 4);
        assertWorkItemsCount(jackCeoCase, 1);
        assertWorkItemsCount(jackCtoCase, 3);

        assertNoDecision(elaineCeoCase, 4, 1, NOT_DECIDED, false);
        assertNoDecision(guybrushCooCase, 4, 1, REVOKE, false);
        assertReviewerDecision(administratorCooCase, ACCEPT, null, 4, 1, USER_CHEESE_OID, ACCEPT, false);
        assertReviewerDecision(administratorCeoCase, ACCEPT, null, 4, 1, USER_CHEESE_OID, ACCEPT, false);
        assertNoDecision(jackCeoCase, 4, 1, NO_RESPONSE, false);
        assertNoDecision(jackCtoCase, 4, 1, NO_RESPONSE, false);

        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, NOT_DECIDED, NOT_DECIDED, null);
        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, REVOKE, REVOKE, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, ACCEPT, ACCEPT, null);
        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, ACCEPT, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, NO_RESPONSE, NO_RESPONSE, null);

        assertPercentComplete(campaignOid, 83, 33, 67);
    }

    @Test
    public void test490CloseFourthStage() throws Exception {
        final String TEST_NAME = "test490CloseFourthStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
		task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.closeCurrentStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign after closing stage 4", campaign);
        assertSanityAfterStageClose(campaign, certificationDefinition, 4);

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("wrong # of cases", 6, caseList.size());

        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCooCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID);
        AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

        /*
Stage4: allMustAccept

Overall: allMustAccept

Case                        Stage1              Stage2                           Stage3                                            Stage4
===============================================================================================================================================
elaine->CEO                 none (A) -> A       elaine A -> A             | A    elaine:null,administrator:ND -> ND  [STOP] | ND
guybrush->COO               cheese: A -> A      admin: RV -> RV   [STOP]  | RV
administrator->COO          none (A) -> A       admin: A -> A             | A    elaine:A,administrator:null -> A           | A    cheese:A -> A | A
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR   cheese:A -> A | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR   cheese:NR -> NR | NR
*/

        assertCaseHistoricOutcomes(elaineCeoCase, ACCEPT, ACCEPT, NOT_DECIDED);
        assertCaseHistoricOutcomes(guybrushCooCase, ACCEPT, REVOKE);
        assertCaseHistoricOutcomes(administratorCooCase, ACCEPT, ACCEPT, ACCEPT, ACCEPT);
        assertCaseHistoricOutcomes(administratorCeoCase, ACCEPT, ACCEPT, NO_RESPONSE, ACCEPT);
        assertCaseHistoricOutcomes(jackCeoCase, ACCEPT, NO_RESPONSE);
        assertCaseHistoricOutcomes(jackCtoCase, ACCEPT, ACCEPT, NO_RESPONSE, NO_RESPONSE);

        assertPercentComplete(campaignOid, 83, 33, 67);
    }

    @Test
    public void test495StartRemediation() throws Exception {
        final String TEST_NAME = "test900StartRemediation";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.startRemediation(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertInProgressOrSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign after remediation start", campaign);
        assertTrue("wrong campaign state: " + campaign.getState(), campaign.getState() == CLOSED || campaign.getState() == IN_REMEDIATION);

        ObjectQuery query = QueryBuilder.queryFor(TaskType.class, prismContext)
                .item(TaskType.F_OBJECT_REF).ref(campaign.getOid())
                .build();
        List<PrismObject<TaskType>> tasks = taskManager.searchObjects(TaskType.class, query, null, result);
        assertEquals("unexpected number of related tasks", 1, tasks.size());
        waitForTaskFinish(tasks.get(0).getOid(), true);

        campaign = getCampaignWithCases(campaignOid);
        assertEquals("wrong campaign state", CLOSED, campaign.getState());
        assertEquals("wrong campaign stage", 5, campaign.getStageNumber());
        assertDefinitionAndOwner(campaign, certificationDefinition);
        assertApproximateTime("end time", new Date(), campaign.getEndTimestamp());
        assertEquals("wrong # of stages", 4, campaign.getStage().size());

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        assertEquals("wrong # of cases", 6, caseList.size());
        AccessCertificationCaseType elaineCeoCase = findCase(caseList, USER_ELAINE_OID, ROLE_CEO_OID);
        AccessCertificationCaseType guybrushCooCase = findCase(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID);
        assertNull("elaineCeoCase.remediedTimestamp", elaineCeoCase.getRemediedTimestamp());
        assertApproximateTime("guybrushCooCase.remediedTimestamp", new Date(), guybrushCooCase.getRemediedTimestamp());

        userElaine = getUser(USER_ELAINE_OID).asObjectable();
        display("userElaine", userElaine);
        assertEquals("wrong # of userElaine's assignments", 5, userElaine.getAssignment().size());

        userGuybrush = getUser(USER_GUYBRUSH_OID).asObjectable();
        display("userGuybrush", userGuybrush);
        assertEquals("wrong # of userGuybrush's assignments", 2, userGuybrush.getAssignment().size());
    }

    @Test
    public void test497Statistics() throws Exception {
        final String TEST_NAME = "test910Statistics";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationCasesStatisticsType stat =
                certificationManager.getCampaignStatistics(campaignOid, false, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

//        AccessCertificationCampaignType campaignWithCases = getCampaignWithCases(campaignOid);
//        display("campaignWithCases", campaignWithCases);

        display("statistics", stat.asPrismContainerValue());
        assertEquals(1, stat.getMarkedAsAccept());
        assertEquals(1, stat.getMarkedAsRevoke());
        assertEquals(1, stat.getMarkedAsRevokeAndRemedied());
        assertEquals(0, stat.getMarkedAsReduce());
        assertEquals(0, stat.getMarkedAsReduceAndRemedied());
        assertEquals(1, stat.getMarkedAsNotDecide());
        assertEquals(3, stat.getWithoutResponse());
    }

    @Test
    public void test499CheckAfterClose() throws Exception {
        final String TEST_NAME = "test920CheckAfterClose";
        TestUtil.displayTestTitle(this, TEST_NAME);
        login(userAdministrator.asPrismObject());

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCertificationBasic.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        waitForCampaignTasks(campaignOid, 20000, result);

        // THEN
        userAdministrator = getUser(USER_ADMINISTRATOR_OID).asObjectable();
        display("administrator", userAdministrator);
        AssignmentType assignment = findAssignmentByTargetRequired(userAdministrator.asPrismObject(), ROLE_COO_OID);
        assertCertificationMetadata(assignment.getMetadata(), SchemaConstants.MODEL_CERTIFICATION_OUTCOME_ACCEPT,
                new HashSet<>(asList(USER_ADMINISTRATOR_OID, USER_ELAINE_OID, USER_CHEESE_OID)), singleton("administrator: ok"));
    }

    @Test
    public void test500Reiterate() throws Exception {
        final String TEST_NAME = "test500Reiterate";
        TestUtil.displayTestTitle(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(TestEscalation.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        dummyTransport.clearMessages();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);

        //certificationManager.closeCampaign(campaignOid, true, task, result);
        certificationManager.reiterateCampaign(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign after reiteration", campaign);
        assertStateStageIteration(campaign, AccessCertificationCampaignStateType.CREATED, 0, 2);
    }

/*
AFTER REITERATION
-----------------
Expected cases, reviewers and decisions/outcomes:

CEO = 00000000-d34d-b33f-f00d-000000000001
COO = 00000000-d34d-b33f-f00d-000000000002

Stage1: oneAcceptAccepts, default: accept, stop on: revoke          (manager)

Case                        Stage1
================================================
administrator->CEO          none (A) -> A
jack->CEO                   none (A) -> A
jack->CTO                   none (A) -> A


Stage2: allMustAccept, default: accept, advance on: accept          (target owner)

Case                        Stage1              Stage2
=============================================================
administrator->CEO          none (A) -> A       "A" from iter 1
jack->CEO                   none (A) -> A       elaine
jack->CTO                   none (A) -> A       "A" from iter 1

From iteration 1:
Case                        Stage1              Stage2                           Stage3                                            Stage4
===============================================================================================================================================
administrator->CEO          none (A) -> A       elaine: A -> A            | A    elaine:NR,administrator:NR -> NR           | NR   cheese:A -> A | NR
jack->CEO                   none (A) -> A       elaine: null -> NR [STOP] | NR
jack->CTO                   none (A) -> A       none (A) -> A             | A    elaine:null,administrator:null -> NR       | NR   cheese:NR -> NR | NR

*/


	@Test
    public void test510OpenNextStage() throws Exception {           // next stage is 2 (because the first one has no work items)
        final String TEST_NAME = "test510OpenNextStage";
        TestUtil.displayTestTitle(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationManager.openNextStage(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 2", campaign);
        assertSanityAfterStageOpen(campaign, certificationDefinition, 2, 2, 5); // stage 1 in iteration 2 was skipped

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
		caseList.removeIf(c -> c.getIteration() != 2);
		assertEquals("Wrong number of certification cases", 3, caseList.size());
		AccessCertificationCaseType administratorCeoCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID);
		AccessCertificationCaseType jackCeoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
		AccessCertificationCaseType jackCtoCase = findCase(caseList, USER_JACK_OID, ROLE_CTO_OID);

		assertCaseReviewers(administratorCeoCase, null, 0, emptyList());
		assertCaseReviewers(jackCeoCase, NO_RESPONSE, 2, singletonList(USER_ELAINE_OID));
		assertCaseReviewers(jackCtoCase, null, 0, emptyList());

		assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, null, NO_RESPONSE, null);
		assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, NO_RESPONSE, NO_RESPONSE, null);
		assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, null, NO_RESPONSE, null);

		//        assertPercentComplete(campaignOid, 17, 17, 0);
		assertCasesCount(campaignOid, 6);

        //assertPercentComplete(campaign, 83, 83, 0);
    }

//    @Test
//    public void test520CloseFirstStage() throws Exception {
//        final String TEST_NAME = "test520CloseFirstStage";
//        TestUtil.displayTestTitle(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
//        task.setOwner(userAdministrator.asPrismObject());
//        OperationResult result = task.getResult();
//
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        certificationManager.closeCurrentStage(campaignOid, task, result);
//
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
//        display("campaign in stage 1", campaign);
//
//        assertSanityAfterStageClose(campaign, certificationDefinition, 1);
//
//        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
//        assertEquals("unexpected # of cases", 6, caseList.size());
//
//        assertCaseOutcome(caseList, USER_ELAINE_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 1);
//        assertCaseOutcome(caseList, USER_GUYBRUSH_OID, ROLE_COO_OID, ACCEPT, ACCEPT, 1);
//        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, ACCEPT, ACCEPT, 1);
//        assertCaseOutcome(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 1);
//        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CEO_OID, ACCEPT, ACCEPT, 1);
//        assertCaseOutcome(caseList, USER_JACK_OID, ROLE_CTO_OID, ACCEPT, ACCEPT, 1);
//
//        assertPercentComplete(campaignOid, 100, 100, 100);
//        assertCasesCount(campaignOid, 6);
//    }

//    @Test
//    public void test530OpenSecondStage() throws Exception {
//        final String TEST_NAME = "test530OpenSecondStage";
//        TestUtil.displayTestTitle(this, TEST_NAME);
//
//        // GIVEN
//        Task task = taskManager.createTaskInstance(TestCriticalRolesCertification.class.getName() + "." + TEST_NAME);
//        task.setOwner(userAdministrator.asPrismObject());
//        OperationResult result = task.getResult();
//
//        // WHEN
//        TestUtil.displayWhen(TEST_NAME);
//        certificationManager.openNextStage(campaignOid, task, result);
//
//        // THEN
//        TestUtil.displayThen(TEST_NAME);
//        result.computeStatus();
//        TestUtil.assertSuccess(result);
//
//        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
//        display("campaign in stage 2", campaign);
//        assertSanityAfterStageOpen(campaign, certificationDefinition, 2, 2, 6);
//
//        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
//    }

}
