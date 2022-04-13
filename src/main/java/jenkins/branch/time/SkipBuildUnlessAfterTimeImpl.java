/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.branch.buildstrategies.time;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import hudson.util.FormValidation;
import java.io.IOException;
import javax.servlet.ServletException;
import org.kohsuke.stapler.QueryParameter;

import org.jenkinsci.plugins.github_branch_source.BranchSCMHead;

import org.jenkinsci.plugins.github_branch_source.GitHubSCMSource;
import org.jenkinsci.plugins.github_branch_source.Connector;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHRepository;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.util.logging.Level;

public class SkipBuildUnlessAfterTimeImpl extends BranchBuildStrategy  {

    private static final Logger LOGGER = Logger.getLogger(SkipBuildUnlessAfterTimeImpl.class.getName());
    public final String buildAfterTime;



        @DataBoundConstructor
         public SkipBuildUnlessAfterTimeImpl(String buildAfterTime) {
             this.buildAfterTime= buildAfterTime;
               
         }


    private Long gettheTime(){
        return Long.parseLong(buildAfterTime);
    }




    private Credentials findCredentials(String cred_id) throws IOException{
        for(Credentials cred : SystemCredentialsProvider.getInstance().getCredentials()){
          if (((StandardCredentials)cred).getId() == cred_id){
            return cred;
          }
        }

        throw IOException("Failed to find credential ID");
    }

    private long getGithubCommitTime(SCMSource source, TaskListener taskListener, ){
        GitHubSCMSource newsource = (GitHubSCMSource)source;
        //Maybe this? https://javadoc.jenkins.io/plugin/github-branch-source/org/jenkinsci/plugins/github_branch_source/GitHubSCMSource.html
        String cred_id = newsource.getCredentialsId();
        taskListener.getLogger().format("Cred ID = %s %n",  cred_id);
        StandardCredentials creds = (StandardCredentials) findCredentials(cred_id);
        GitHub github = Connector.connect(newsource.getApiUri(), creds);                
        GHRepository repo = github.getRepository(newsource.getRepoOwner()+"/"+newsource.getRepository());
        long milliseconds = repo.getCommit(head.toString()).getCommitDate().getTime();
        return milliseconds
    }



    @Override
    public boolean isUpdatingLastBuiltRevisionWithNoBuild(@NonNull TaskListener taskListener){
        return true;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAutomaticBuild(@NonNull SCMSource source, 
                                    @NonNull SCMHead head, 
                                    @NonNull SCMRevision currRevision,
                                    @CheckForNull SCMRevision lastBuiltRevision, 
                                    @CheckForNull SCMRevision lastSeenRevision, 
                                    @NonNull TaskListener taskListener,
                                    @CheckForNull SCMEvent scmEvent) {

        try{
            taskListener.getLogger().format("Tick setting = %s %n", String.valueOf(gettheTime()));
            
            if (scmEvent != null){
                //Compare to passed in time
                // If passed in time is greater than scmevent, do not build...
                taskListener.getLogger().format("SCMEvent time is %s, ticks configured is %s %n",String.valueOf(scmEvent.getTimestamp()), String.valueOf(gettheTime()));
                return scmEvent.getTimestamp() > gettheTime()
            }
            else{
                //Only happens on indexing... or missed events
                //Is it a github SCM thing?
                if (source instanceof GitHubSCMSource){

                    long milliseconds = getGithubCommitTime(source, taskListener)
                    if (milliseconds > gettheTime()){
                        taskListener.getLogger().format("Last commit from Github(%s) is greater than ticks configured(%s) %n",String.valueOf(milliseconds), String.valueOf(gettheTime()));
                        return true;
                    }
                    else{
                        taskListener.getLogger().format("Last commit from Github(%s) is less than ticks configured(%s) %n",String.valueOf(milliseconds), String.valueOf(gettheTime()));
                        return false;
                    }
                }
                else{

                    taskListener.getLogger().format("Not a github source, returning true %n");
                    return true;
                
                }
            }
        }
        catch (Exception e) {
                taskListener.getLogger().format("Error in SkipBuildUnlessAfterTimeImpl %n");
                e.printStackTrace(taskListener.getLogger());

                LOGGER.log(Level.SEVERE, "Error in SkipBuildUnlessAfterTimeImpl", e);
                return false; //Default to false to make it so the build would not run by default.
            }

    }


    //@Override
    //public DescriptorImpl getDescriptor() {
    //    return new DescriptorImpl();
    //}


    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {
         

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Skip Build Until After a Date/Time";
        }

        public FormValidation doformValidation(@QueryParameter("buildAfterTime") final String buildAfterTime) throws IOException, ServletException {
            try {
                 Long.parseLong(buildAfterTime); 

                return FormValidation.ok("Success");
            } catch (Exception e) {
                return FormValidation.error("Client error : "+e.getMessage());
            }
        }

    }

}
