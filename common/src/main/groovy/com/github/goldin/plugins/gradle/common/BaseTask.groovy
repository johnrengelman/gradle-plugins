package com.github.goldin.plugins.gradle.common
import org.apache.tools.ant.DirectoryScanner
import org.gcontracts.annotations.Ensures
import org.gcontracts.annotations.Requires
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecSpec

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.regex.Pattern


/**
 * Base helper task class to be extended by other tasks
 */
abstract class BaseTask extends DefaultTask
{
    Gradle  gradle
    File    rootDir
    String  version
    long    startTime
    Object  extension
    final   DateFormat dateFormatter        = new SimpleDateFormat( 'dd MMM, EEEE, yyyy, HH:mm:ss (zzzzzz:\'GMT\'ZZZZZZ)', Locale.ENGLISH )
    final   Map<String, String>  env        = System.getenv().asImmutable()
    final   Map<String, String>  properties = ( Map<String , String> ) System.properties.asImmutable()


    /**
     * Should be implemented by task.
     * Called after all fields are initialized.
     */
    abstract void taskAction()


    @TaskAction
    @Requires({ project })
    @Ensures({ gradle && rootDir.directory && version && ( startTime > 1352215376393 )})
    final void doTask()
    {
        this.gradle    = project.gradle
        this.rootDir   = project.rootDir
        this.version   = project.version
        this.startTime = System.currentTimeMillis()
        taskAction()
    }


    @Requires({ c != null })
    @Ensures({ result != null })
    final String s( Collection c ){ s( c.size()) }

    @Requires({ j > -1 })
    @Ensures({ result != null })
    final String s( Number j ){ j == 1 ? '' : 's' }


    @Requires({ command && directory.directory })
    @Ensures({ result != null })
    final String gitExec( String command, File directory, boolean failOnError = true )
    {
        exec( 'git', command.tokenize(), directory, failOnError )
    }


    /**
     * Executes the command specified.
     *
     * @param command     command to execute
     * @param arguments   command arguments
     * @param directory   process working directory
     * @param failOnError whether execution should fail in case of an error
     *
     * @return process standard and error output
     */
    @Requires({ command && ( ! command.contains( ' ' )) && ( arguments != null ) })
    @Ensures({ result != null })
    final String exec( String       command,
                       List<String> arguments   = [],
                       File         directory   = null,
                       boolean      failOnError = true )
    {
        final commandDescription = "[$command]${ arguments ? ' with arguments ' + arguments : '' }${ directory ? ' in [' + directory.canonicalPath + ']' : '' }"
        if ( logger.infoEnabled )
        {
            logger.info( "Running $commandDescription" )
        }

        final outputStream = new ByteArrayOutputStream()

        try
        {
            project.exec {
                ExecSpec spec ->
                spec.with {
                    executable( command )
                    if ( arguments ) { args( arguments ) }
                    standardOutput = outputStream
                    errorOutput    = outputStream
                    if ( directory ) { workingDir = directory }
                }
            }
        }
        catch ( Throwable error )
        {
            if ( failOnError )
            {
                throw new GradleException( "Failed to execute $commandDescription, output is [${ outputStream.toString().trim()}]",
                                           error )
            }
        }

        if ( logger.debugEnabled )
        {
            final output = outputStream.toString().trim()
            if ( output ) { logger.debug( '>> ' + output.readLines().join( '\n>> ' )) }
        }

        outputStream.toString().trim()
    }


    /**
     * Retrieves extension of the type specified.
     *
     * @param extensionName name of extension
     * @param extensionType type of extension
     * @return extension of the type specified
     */
    @Requires({ extensionName && extensionType })
    @Ensures ({ result })
    final public <T> T extension( String extensionName, Class<T> extensionType )
    {
        if ( ! this.extension )
        {
            this.extension = project[ extensionName ]
            assert extensionType.isInstance( this.extension ), \
                   "Project extension [$extensionName] is of type [${ extension.getClass().name }], " +
                   "should be of type [${ extensionType.name }]"
        }

        (( T ) this.extension )
    }


    /**
     * Retrieves files (and directories, if required) given base directory and inclusion/exclusion patterns.
     * Symbolic links are not followed.
     *
     * @param baseDirectory      files base directory
     * @param includePatterns    patterns to use for including files, all files are included if null
     * @param excludePatterns    patterns to use for excluding files, no files are excluded if null
     * @param isCaseSensitive    whether or not include and exclude patterns are matched in a case sensitive way
     * @param includeDirectories whether directories included should be returned as well
     * @param failIfNotFound     whether execution should fail if no files were found
     *
     * @return files under base directory specified passing an inclusion/exclusion patterns
     */
    @Requires({ baseDirectory.directory })
    final List<File> files ( File         baseDirectory,
                             List<String> includePatterns    = null,
                             List<String> excludePatterns    = null,
                             boolean      isCaseSensitive    = true,
                             boolean      includeDirectories = false,
                             boolean      failIfNotFound     = true )
    {
        def scanner = new DirectoryScanner()

        scanner.with {
            basedir           = baseDirectory
            includes          = includePatterns as String[]
            excludes          = excludePatterns as String[]
            caseSensitive     = isCaseSensitive
            errorOnMissingDir = true
            followSymlinks    = false
            scan()
        }

        def files = []
        scanner.includedFiles.each { String filePath -> files << new File( baseDirectory, filePath ) }

        if ( includeDirectories )
        {
            scanner.includedDirectories.findAll { it }.each { String dirPath -> files << new File( baseDirectory, dirPath ) }
        }

        assert ( files || ( ! failIfNotFound )), \
               "No files are included by parent dir [$baseDirectory] and include/exclude patterns ${ includePatterns ?: [] }/${ excludePatterns ?: [] }"

        files
    }


    /**
     * Archives files specified.
     *
     * @param files files to archive
     * @return first file specified
     */
    @Requires({ files })
    @Ensures({ result })
    final File zip ( File ... files )
    {
        assert files.every { it && it.file }
        files.each { File f -> zip( project.file( "${ f.canonicalPath }.zip" )){ ant.zipfileset( file: f.canonicalPath ) }}
        files.first()
    }


    /**
     * Creates an archive specified.
     *
     * @param archive     archive to create
     * @param zipClosure  closure to run in {@code ant.zip{ .. }} context
     * @return archive created
     */
    @Requires({ archive && zipClosure })
    @Ensures ({ result.file })
    final File zip ( File archive, Closure zipClosure )
    {
        project.delete( archive )
        ant.zip( destfile: archive, duplicate: 'fail', whenempty: 'fail', level: 9 ){ zipClosure() }
        assert archive.file, "Failed to create [$archive.canonicalPath] using 'ant.zip( .. ){ .. }'"
        archive
    }


    /**
     * Adds files specified to the archive through {@code ant.zipfileset( file: file, prefix: prefix )}.
     *
     * @param archive  archive to add files specified
     * @param files    files to add to the archive
     * @param prefix   files prefix in the archive
     * @param includes patterns of files to include, all files are included if null or empty
     * @param excludes patterns of files to exclude, no files are excluded if null or empty
     */
    final void addFilesToArchive ( File             archive,
                                   Collection<File> files,
                                   String           prefix,
                                   List<String>     includes = null,
                                   List<String>     excludes = null )
    {
        files.each { addFileToArchive( archive, it, prefix, includes, excludes )}
    }


    /**
     * Adds file specified to the archive through {@code ant.zipfileset( file: file, prefix: prefix )}.
     *
     * @param archive  archive to add files specified
     * @param file     file to add to the archive
     * @param prefix   files prefix in the archive
     * @param includes patterns of files to include, all files are included if null or empty
     * @param excludes patterns of files to exclude, no files are excluded if null or empty
     */
    @SuppressWarnings([ 'GroovyAssignmentToMethodParameter' ])
    @Requires({ archive && file && ( prefix != null ) })
    final void addFileToArchive ( File         archive,
                                  File         file,
                                  String       prefix,
                                  List<String> includes = null,
                                  List<String> excludes = null )
    {
        prefix = prefix.startsWith( '/' ) ? prefix.substring( 1 )                      : prefix
        prefix = prefix.endsWith  ( '/' ) ? prefix.substring( 0, prefix.length() - 1 ) : prefix

        assert ( file.file || file.directory ), \
               "[${ file.canonicalPath }] - not found when creating [${ archive.canonicalPath }]"

        final arguments = [ ( file.file ? 'file' : 'dir' ) : file, prefix: prefix ]
        if ( includes ) { arguments[ 'includes' ] = includes.join( ',' )}
        if ( excludes ) { arguments[ 'excludes' ] = excludes.join( ',' )}

        ant.zipfileset( arguments )
    }


    /**
     * Verifies resources specified can be found in files provided.
     *
     * @param files     files to check
     * @param resources resources to locate in the files provided
     */
    @Requires({ files && resources })
    final void checkResources( Collection<File> files, String ... resources )
    {
        final cl = new URLClassLoader( files*.toURI()*.toURL() as URL[] )
        resources.each { assert cl.getResource( it ), "No '$it' resource found in $files" }
    }


    @Requires({ dir })
    @Ensures({ ( result == dir ) && ( result.directory ) && ( ! result.list())})
    final File makeEmptyDirectory( File dir )
    {
        assert (( ! dir.exists()) || ( project.delete( dir ) && ( ! dir.exists())))
        project.mkdir( dir )
    }


    /**
     * Validates XML specified with Schema provided.
     *
     * @param xml    XML to validate
     * @param schema schema to validate with
     * @return       same XML instance
     * @throws       GradleException if validation fails
     */
    @Requires({ xml && schema })
    final String validateXml( String xml, String schema )
    {
        try
        {
            SchemaFactory.newInstance( XMLConstants.W3C_XML_SCHEMA_NS_URI ).
            newSchema( new StreamSource( new StringReader( schema ))).
            newValidator().
            validate( new StreamSource( new StringReader( xml )))
        }
        catch ( e )
        {
            throw new GradleException( "Failed to validate XML\n[$xml]\nusing schema\n[$schema]", e )
        }

        xml
    }


    /**
     * Retrieves all appearances of the first capturing group of the pattern specified in a String.
     */
    @Requires({ s && p })
    @Ensures({ result != null })
    final List<String> findAll( String s, Pattern p ){ s.findAll ( p ) { it[ 1 ] }}


    /**
     * Retrieves first appearance of the first capturing group of the pattern specified in a String.
     */
    @Requires({ s && p })
    final String find( String s, Pattern p ){ s.find ( p ) { it[ 1 ] }}
}
