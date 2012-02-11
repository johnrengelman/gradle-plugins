package com.goldin.plugins.gradle.about

import com.goldin.plugins.gradle.util.BaseTask
import java.text.SimpleDateFormat
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ProjectReportsPlugin
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.diagnostics.internal.AsciiReportRenderer

/**
 * {@link AboutPlugin} task
 */
class AboutTask extends BaseTask
{
   /**
    * Line to be used in dependencies report
    */
    private static final String LINE = '-' * 80

    private final Map<String, String> env = System.getenv()
    private final Logger              log = project.logger


    /**
     * Retrieves current plugin extension object.
     * @return current plugin extension object
     */
    private AboutPluginExtension ext () { ( AboutPluginExtension ) project[ 'about' ] }


    private String padLines ( String s )
    {
        if ( ! s ) { return '' }

        def padWidth = ' Status        : ['.size()
        def lines    = s.readLines()

        ( lines ? ( lines[ 0 ] + (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { '|' + ( ' ' * padWidth ) + it }.join( '\n' ) :
                                                         '' )) :
                  '' )
    }


    private String exec ( String command, File directory = null )
    {
        final process = command.execute(( List ) null, directory )
        ( process.text + process.err.text ).trim()
    }


    private String find ( String prefix, String command ) { find( prefix, exec( command ).readLines()) }
    private String find ( String prefix, List<String> l ) { l.find{ it.startsWith( prefix ) }?.replace( prefix, '' )?.trim() ?: '' }
    private String sort ( Map<String,String> map )
    {
        def maxKey = map.keySet()*.size().max() + 3
        map.sort().collect { String key, String value ->
                             "[$key]".padRight( maxKey ) + ":[$value]" }.
                   join( '\n' )
    }


    void taskAction()
    {
        final directory = ext().directory ?: jarTask.destinationDir
        final fileName  = ext().fileName  ?: "about-${group}-${name}-${version}.txt"
        final split     = { String s -> ( List<String> )( s ? s.split( /,/ ).toList()*.trim().findAll{ it } : null ) }
        final files     = files( directory, split( ext().include ), split( ext().exclude ))
        final tempFile  = new File( jarTask.temporaryDir, fileName )
        final prefix    = (( ext().prefix == '/' ) ? '' : ext().prefix )

        log.info( "Generating \"about\" in [$tempFile.canonicalPath] .." )

        tempFile.write(( ' Generated by http://evgeny-goldin.com/wiki/Gradle-about-plugin\n' +
                         scmContent() + buildContent() + serverContent()).
                       stripMargin().readLines()*.replaceAll( /\s+$/, '' ).findAll { it }. // Deleting empty lines
                       join(( 'windows' == ext().endOfLine ) ? '\r\n' : '\n' ))

        log.info( "Generated  \"about\" in [$tempFile.canonicalPath]" )

        for ( file in files )
        {
            def aboutPath = "$file.canonicalPath/$prefix${ prefix ? '/' : '' }$fileName"
            log.info( "Adding \"about\" to [$aboutPath] .." )

            project.ant.zip( destfile : file.canonicalPath,
                             update   : true ){
                zipfileset( file   : tempFile.canonicalPath,
                            prefix : prefix )
            }

            log.info( "Added  \"about\" to [$aboutPath]" )
        }

        project.delete( tempFile )
    }


    String jenkinsContent()
    {
        // https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project

        """
        |===============================================================================
        | Jenkins Info
        |===============================================================================
        | Server        : [${ env[ 'JENKINS_URL' ] }]
        | Job           : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log           : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String hudsonContent()
    {
        // http://weblogs.java.net/blog/johnsmart/archive/2008/03/using_hudson_en.html

        """
        |===============================================================================
        | Hudson Info
        |===============================================================================
        | Server        : [${ env[ 'HUDSON_URL' ] }]
        | Job           : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log           : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    String teamcityContent()
    {
        // http://confluence.jetbrains.net/display/TCD65/Predefined+Build+Parameters

        """
        |===============================================================================
        | TeamCity Info
        |===============================================================================
        | Project Name  : [${ env[ 'TEAMCITY_PROJECT_NAME' ] }]
        | Build Config  : [${ env[ 'TEAMCITY_BUILDCONF_NAME' ] }]
        | Build Number  : [${ env[ 'BUILD_NUMBER' ] }]"""
    }


    String serverContent()
    {
        ( env[ 'JENKINS_URL'      ] ? jenkinsContent()  :
          env[ 'HUDSON_URL'       ] ? hudsonContent()   :
          env[ 'TEAMCITY_VERSION' ] ? teamcityContent() :
                                      '' )  +
        '''
        |==============================================================================='''
    }

    String buildContent ()
    {
        def props  = System.properties
        def format = new SimpleDateFormat( "dd MMM, EEEE, yyyy, HH:mm:ss (zzzzzz:'GMT'ZZZZZZ)", Locale.ENGLISH )

        """
        |===============================================================================
        | Build Info
        |===============================================================================
        | Host          : [${ env[ 'COMPUTERNAME' ] ?: env[ 'HOSTNAME' ] ?: exec( 'hostname' ) ?: '' }]
        | Build Time    : [${ format.format( new Date()) }]
        | User          : [${ props[ 'user.name' ] }]
        | ${ ext().dumpPaths ? 'Directory     : [' + props[ 'user.dir' ] + ']': '' }
        | Java          : [${ props[ 'java.version' ] }][${ props[ 'java.vm.vendor' ] }]${ ext().dumpPaths ? '[' + props[ 'java.home' ] + ']' : '' }[${ props[ 'java.vm.name' ] }]
        | OS            : [${ props[ 'os.name' ] }][${ props[ 'os.arch' ] }][${ props[ 'os.version' ] }]
        |===============================================================================
        | Gradle Info
        |===============================================================================
        | ${ ext().dumpPaths ? 'Home          : [' + project.gradle.gradleHomeDir.canonicalPath + ']' : '' }
        | ${ ext().dumpPaths ? 'Basedir       : [' + rootDir.canonicalPath + ']': '' }
        | ${ ext().dumpPaths ? 'Build file    : [' + project.buildFile.canonicalPath + ']' : '' }
        | GRADLE_OPTS   : [${ env[ 'GRADLE_OPTS' ] ?: '' }]
        | Version       : [${ project.gradle.gradleVersion }]
        | Project       : [${ ext().dumpPaths ? project.toString() : project.toString().replaceAll( /\s+@.+/, '' )}]
        | Tasks         : ${ project.gradle.startParameter.taskNames }
        | Coordinates   : [$group:$name:$version]
        | ${ ext().dumpDependencies ? 'Dependencies  : [' + padLines( dependenciesContent()) + ']' : '' }""" +

        ( ext().dumpSystem ?

        """
        |===============================================================================
        | System Properties
        |===============================================================================
        |${ sort( props ) }""" : '' ) +

        ( ext().dumpEnv ?

        """
        |===============================================================================
        | Environment Variables
        |===============================================================================
        |${ sort( env ) }""" : '' )
    }


    String dependenciesContent ()
    {
        project.plugins.apply( ProjectReportsPlugin )

        DependencyReportTask task = ( DependencyReportTask ) project.tasks[ ProjectReportsPlugin.DEPENDENCY_REPORT ]
        def renderer              = new AsciiReportRenderer()
        def file                  = new File( project.buildDir, 'dependencies.txt' )
        assert ( ! file.isFile()) || file.delete()

        renderer.outputFile       = file
        task.renderer             = renderer
        task.generate( project )

        assert file.isFile(), "File [$file.canonicalPath] was not created by dependency report"
        "$LINE\n" + file.text.replaceAll( /(?m)^\s*$/, LINE ) // Empty lines are replaced by LINE
    }


    String scmContent()
    {
        if ( ! ext().dumpSCM ) { return '' }

        File   svnDir           = new File( project.rootDir, '.svn' )
        String svnVersion       = null
        String svnStatus        = null
        String gitVersion       = null
        String gitStatusCommand = null
        String gitStatus        = null

        /**
         * Trying SVN
         */

        if ( svnDir.isDirectory())
        {
            svnVersion = exec( 'svn --version' )
            if ( svnVersion.toLowerCase().contains( 'svn, version' ))
            {
                svnStatus = exec( "svn status ${}.canonicalPath" )
                if (( ! svnStatus.contains( 'is not a working copy' )) &&
                    ( ! svnStatus.contains( 'containing working copy admin area is missing' )))
                {
                    return svnContent( svnStatus )
                }
            }
        }

        /**
         * Trying Git
         */

        gitVersion = exec( 'git --version' )

        if ( gitVersion.contains( 'git version' ))
        {
            gitStatusCommand = 'git status' + ( ext().gitStatusProject ? '' : ' ' + rootDir.canonicalPath )
            gitStatus        = exec( gitStatusCommand )

            if ( ! gitStatus.contains( 'fatal: Not a git repository' ))
            {
                return gitContent( gitStatus )
            }
        }

        """
        |===============================================================================
        | SCM Info
        |===============================================================================
        | Unsupported SCM system: either project is not managed by SVN/Git or corresponding command-line clients are not available.
        | Tried SVN:
        | ~~~~~~~~~~
        | [$svnDir.canonicalPath] - ${ svnDir.isDirectory() ? 'found' : 'not found' }
        | ${ svnVersion ? '"svn --version" returned [' + svnVersion + ']'                           : '' }
        | ${ svnStatus  ? '"svn status ' + rootDir.canonicalPath + '" returned [' + svnStatus + ']' : '' }
        | Tried Git:
        | ~~~~~~~~~~
        | ${ gitVersion ? '"git --version" returned [' + gitVersion + ']'                            : '' }
        | ${ gitStatus  ? '"' + gitStatusCommand + '" returned [' + gitStatus + ']'                  : '' }"""
    }


    String svnContent( String svnStatus )
    {
        def svnInfo = exec( "svn info ${rootDir.canonicalPath}" ).readLines()
        def commit  = exec( 'svn log -l 1' ).readLines()[ 1 ]

        """
        |===============================================================================
        | SVN Info
        |===============================================================================
        | Repository    : [${ find( 'URL:',      svnInfo )}]
        | Revision      : [${ find( 'Revision:', svnInfo )}]
        | Status        : [${ padLines( svnStatus ) }]
        | Last Commit   : [$commit]
        | Commit Date   : [${ commit.split( '\\|' )[ 2 ].trim() }]
        | Commit Author : [${ commit.split( '\\|' )[ 1 ].trim() }]"""
    }


    String gitContent( String gitStatus )
    {
        def gitLog = exec( 'git log -1' ).readLines()

        """
        |===============================================================================
        | Git Info
        |===============================================================================
        | Repositories  : [${ padLines( exec( 'git remote -v' )) }]
        | Branch        : [${ find( '# On branch', 'git status' ) }]
        | ${ ext().gitStatusProject ? 'Project' : 'Basedir' } Status: [${ padLines( gitStatus ) }]
        | Last Commit   : [${ find( 'commit',      gitLog )}]
        | Commit Date   : [${ find( 'Date:',       gitLog )}]
        | Commit Author : [${ find( 'Author:',     gitLog )}]"""
    }
}
