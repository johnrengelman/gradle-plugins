package com.github.goldin.plugins.gradle.about

import com.github.goldin.plugins.gradle.common.BaseTask
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.gradle.api.plugins.ProjectReportsPlugin
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.diagnostics.internal.AsciiReportRenderer


/**
 * {@link AboutPlugin} task
 */
class AboutTask extends BaseTask
{
    private AboutExtension ext () { extension ( AboutPlugin.EXTENSION_NAME, AboutExtension ) }
    private static final String SEPARATOR = '|==============================================================================='


    @Requires({ ( s != null ) && prefix })
    @Ensures({ result != null })
    private String padLines ( String s, String prefix )
    {
        final lines = s.readLines()
        ( lines ? ( lines[ 0 ] + (( lines.size() > 1 ) ? '\n' + lines[ 1 .. -1 ].collect { '|' + ( ' ' * prefix.size()) + it }.join( '\n' ) :
                                                         '' )) :
                  '' )
    }



    @Requires({ prefix && ( list != null ) })
    @Ensures({ result != null })
    private String find ( String prefix, List<String> list )
    {
        list.find{ it.startsWith( prefix ) }?.replace( prefix, '' )?.trim() ?: ''
    }


    @Requires({ map })
    @Ensures({ result })
    private String sort ( Map<String,?> map )
    {
        def maxKey = map.keySet()*.size().max() + 3
        map.sort().collect { String key, Object value -> "[$key]".padRight( maxKey ) + ":[$value]" }.
                   join( '\n' )
    }


    void taskAction()
    {
        final ext       = ext()
        final directory = ext.directory ?: project.buildDir
        final fileName  = ext.fileName  ?: 'about.txt'
        final split     = { String s -> s ? s.split( ',' )*.trim().grep() : null }
        final files     = files( directory, split( ext.include ), split( ext.exclude ))
        final tempFile  = new File( temporaryDir, fileName )
        final prefix    = (( ext.prefix == '/' ) ? '' : ext.prefix )

        logger.info( "Generating \"about\" in [$tempFile.canonicalPath] .." )

        tempFile.write(( ' Generated by http://evgeny-goldin.com/wiki/Gradle-about-plugin\n' +
                         serverContent() + scmContent() + buildContent()).
                       stripMargin().readLines()*.replaceAll( /\s+$/, '' ).grep().
                       join(( 'windows' == ext.endOfLine ) ? '\r\n' : '\n' ))

        logger.info( "Generated  \"about\" in [$tempFile.canonicalPath]" )

        for ( file in files )
        {
            final aboutPath = "$file.canonicalPath!$prefix${ ( prefix && ( ! prefix.endsWith( '/' ))) ? '/' : '' }$fileName"
            logger.info( "Adding \"about\" to [$aboutPath] .." )
            ant.zip( destfile : file.canonicalPath, update : true ){ zipfileset( file : tempFile.canonicalPath, prefix : prefix )}
            logger.info( "Added  \"about\" to [$aboutPath]" )
        }

        assert project.delete( tempFile )
    }


    @Ensures({ result })
    String jenkinsContent()
    {
        // https://wiki.jenkins-ci.org/display/JENKINS/Building+a+software+project

        """
        $SEPARATOR
        | Jenkins Info
        $SEPARATOR
        | Server         : [${ env[ 'JENKINS_URL' ] }]
        | Job            : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log            : [${ env[ 'JENKINS_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    @Ensures({ result })
    String hudsonContent()
    {
        // http://weblogs.java.net/blog/johnsmart/archive/2008/03/using_hudson_en.html

        """
        $SEPARATOR
        | Hudson Info
        $SEPARATOR
        | Server         : [${ env[ 'HUDSON_URL' ] }]
        | Job            : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/]
        | Log            : [${ env[ 'HUDSON_URL' ] }job/${ env[ 'JOB_NAME' ] }/${ env[ 'BUILD_NUMBER' ]}/console]"""
    }


    @Ensures({ result })
    String teamcityContent()
    {
        // http://confluence.jetbrains.net/display/TCD65/Predefined+Build+Parameters
        // http://confluence.jetbrains.net/display/TCD7/Predefined+Build+Parameters

        final urlMessage  = 'Define \'TEAMCITY_URL\' environment variable and make sure \'-Dteamcity.build.id\' specified when job starts'
        final buildId     = properties[ 'teamcity.build.id' ]
        final teamCityUrl = ( env[ 'TEAMCITY_URL' ]?.replaceAll( /(?<!\\|\/)(\\|\/)*$/, '/' )       ?: '' )
        final buildUrl    = ( buildId && teamCityUrl ? "${teamCityUrl}viewLog.html?buildId=$buildId" : '' )
        final logUrl      = ( buildUrl               ? "$buildUrl&tab=buildLog"                      : '' )

        """
        $SEPARATOR
        | TeamCity Info
        $SEPARATOR
        | Server         : [${ teamCityUrl ?: urlMessage }]
        | Job            : [${ buildUrl    ?: urlMessage }]
        | Log            : [${ logUrl      ?: urlMessage }]
        | Server Version : [${ env[ 'TEAMCITY_VERSION' ] }]
        | Project        : [${ env[ 'TEAMCITY_PROJECT_NAME' ] }]
        | Configuration  : [${ env[ 'TEAMCITY_BUILDCONF_NAME' ] }]
        | Build Number   : [${ env[ 'BUILD_NUMBER' ] }]
        | Personal Build : [${ env[ 'BUILD_IS_PERSONAL' ] ?: 'false' }]"""
    }


    @Ensures({ result != null })
    String serverContent()
    {
        ( env[ 'JENKINS_URL'      ] ? jenkinsContent () :
          env[ 'HUDSON_URL'       ] ? hudsonContent  () :
          env[ 'TEAMCITY_VERSION' ] ? teamcityContent() :
                                      '' )
    }


    @Ensures({ result != null })
    private String hostname()
    {
        try { env[ 'COMPUTERNAME' ] ?: env[ 'HOSTNAME' ] ?: exec( 'hostname' ) ?: '' }
        catch( Throwable ignored ){ 'Unknown' }
    }


    @Ensures({ result })
    String buildContent ()
    {
        final ext                 = ext()
        final includeDependencies = ( ext.includeDependencies != false ) && ( ext.includeDependencies != 'false' )

        """
        $SEPARATOR
        | Build Info
        $SEPARATOR
        | Host          : [${ hostname() }]/[${ InetAddress.localHost.hostAddress }]
        | Time          : [${ dateFormatter.format( new Date()) }]
        | User          : [${ properties[ 'user.name' ] }]
        | ${ ext.includePaths ? 'Directory     : [' + properties[ 'user.dir' ] + ']': '' }
        | Java          : [${ properties[ 'java.version' ] }][${ properties[ 'java.vm.vendor' ] }]${ ext.includePaths ? '[' + properties[ 'java.home' ] + ']' : '' }[${ properties[ 'java.vm.name' ] }]
        | OS            : [${ properties[ 'os.name' ] }][${ properties[ 'os.arch' ] }][${ properties[ 'os.version' ] }]
        $SEPARATOR
        | Gradle Info
        $SEPARATOR
        | Version       : [${ gradle.gradleVersion }]
        | ${ ext.includePaths ? 'Home          : [' + gradle.gradleHomeDir.canonicalPath + ']' : '' }
        | ${ ext.includePaths ? 'Project dir   : [' + project.projectDir.canonicalPath + ']': '' }
        | ${ ext.includePaths ? 'Build file    : [' + ( project.buildFile ?: project.rootProject.buildFile ).canonicalPath + ']' : '' }
        | GRADLE_OPTS   : [${ env[ 'GRADLE_OPTS' ] ?: '' }]
        | Project       : [${ ext.includePaths ? project.toString() : project.toString().replaceAll( /\s+@.+/, '' )}]
        | Tasks         : ${ gradle.startParameter.taskNames }
        | Coordinates   : [$project.group:$project.name:$project.version]
        | ${ includeDependencies ? 'Dependencies  : [' + padLines( dependenciesContent(), 'Dependencies  : [' ) + ']' : '' }""" +

        ( ext.includeProperties ?

        """
        $SEPARATOR
        | Gradle Properties
        $SEPARATOR
        |${ sort( project.properties ) }""" : '' ) +

        ( ext.includeSystem ?

        """
        $SEPARATOR
        | System Properties
        $SEPARATOR
        |${ sort( properties ) }""" : '' ) +

        ( ext.includeEnv ?

        """
        $SEPARATOR
        | Environment Variables
        $SEPARATOR
        |${ sort( env ) }""" : '' )
    }


    @Ensures({ result })
    String dependenciesContent ()
    {
        final ext = ext()
        assert ( ext.includeDependencies != false ) && ( ext.includeDependencies != 'false' )

        project.plugins.apply( ProjectReportsPlugin )

        final task          = ( DependencyReportTask ) project.tasks[ ProjectReportsPlugin.DEPENDENCY_REPORT ]
        final renderer      = new AsciiReportRenderer()
        final file          = new File( project.buildDir, "${ this.class.name }-dependencies.txt" )
        final line          = '-' * 80
        assert (( ! file.file ) || project.delete( file )), "Unable to delete [$file.canonicalPath]"

        renderer.outputFile = file
        task.renderer       = renderer
        task.generate( project )

        assert file.file, "File [$file.canonicalPath] was not created by dependency report"
        final String report = ( ext.includeDependencies instanceof List ) ?
            file.text.split( '\n\n' ).findAll { find( it, ext.configurationNamePattern ) in ext.includeDependencies }.join( '\n\n' ) :
            file.text

        report = "$line\n" + report.replaceAll( /(?m)^\s*$/, line ) // Empty lines replaced by $line
        assert project.delete( file ), "Unable to delete [$file.canonicalPath]"
        report
    }


    @Ensures({ result })
    String scmContent()
    {
        final ext = ext()
        if ( ! ext.includeSCM ) { return '' }

        /**
         * Trying Git
         */

        final gitVersion = gitExec( '--version', rootDir, false )
        final gitStatus  = gitVersion.contains( 'git version' ) ? gitExec( 'status', rootDir, false ) : ''

        if ( gitStatus && ( ! gitStatus.contains( 'fatal: Not a git repository' )))
        {
            /**
             * d7d53c1
             * d7d53c1f5eeba85cc02d4522990a020f02dea2b7
             * Sun, 7 Oct 2012 23:01:37 +0200
             * Evgeny Goldin
             * evgenyg@gmail.com
             * CodeNarc fix
             */
            final gitLog = gitExec( 'log -1 --format=format:%h%n%H%n%cD%n%cN%n%ce%n%B', rootDir ).readLines()*.trim()

            """
            $SEPARATOR
            | Git Info
            $SEPARATOR
            | Git Version    : [${ gitVersion.replace( 'git version', '' ).trim() }]
            | Repositories   : [${ padLines( gitExec( 'remote -v', rootDir ), ' Repositories   : [' ) }]
            | Branch         : [${ find( '# On branch', gitStatus.readLines()) }]
            | Git Status     : [${ padLines( gitStatus, ' Git Status     : [' ) }]
            | Commit         : [${ gitLog[ 0 ] }][${ gitLog[ 1 ] }]
            | Commit Date    : [${ gitLog[ 2 ] }]
            | Commit Author  : [${ gitLog[ 3 ] } <${ gitLog[ 4 ] }>]
            | Commit Message : [${ gitLog.size() > 5 ? padLines( gitLog[ 5 .. -1 ].join( '\n' ), ' Commit Message : [' ) : '' }]"""
        }
        else
        {
            """
            $SEPARATOR
            | SCM Info
            $SEPARATOR
            | Unsupported SCM system: either project is not managed by Git or command-line client is not available.
            | Tried Git:
            | ~~~~~~~~~~
            | "git --version" returned [${ padLines( gitVersion, ' "git --version" returned [' ) }]
            |${ gitStatus ? '"git status" returned [' + padLines( gitStatus, '"git status" returned [' ) + ']' : '' }"""
        }
    }
}
