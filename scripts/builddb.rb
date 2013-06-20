#!/usr/bin/env ruby


$:.unshift File.join(File.dirname(__FILE__), "ruby", "lib")

require "ehriutils"
require "fileutils"

USER_ID = ARGV.shift || ENV["USER"]
DROPBOX = "#{ENV["HOME"]}/Dropbox/EHRI-WP19-20/TestData"
EAG_DATA_DIR = "eag"
USHMM_DATA_DIR = "ead"
EAD_DATA_DIR = "icaatom-export"
USHMM_ID = "us-005578"
USER_FIXTURES = "#{ENV["HOME"]}/Dropbox/EHRI/users.yaml"
WL_EAD_DIR = "#{DROPBOX}/wiener-library"
WL_ID = "gb-003348"
THES_RDF = "#{DROPBOX}/ehri-skos.rdf"
CAMPS_RDF = "#{DROPBOX}/EHRI-Camps-structure.rdf"
GHETTOS_RDF = "#{DROPBOX}/EHRI-Ghettos-structure.rdf"
PERS_CSV = "#{DROPBOX}/Personalities-12-03-2013-utf8.csv"

# Check stuff works
EhriUtils::check_env

if Dir.exist?(ENV["NEO4J_DB"])
  # Check if the graph DB exists
  OPTS = {
    1 => "Yes",
    2 => "No",
    3 => "Backup",
    4 => "Delete"
  }

  puts "Neo4j database \"#{ENV["NEO4J_DB"]}\" already exists. Continue?"
  OPTS.each { |opt, text|
    puts "#{opt}) #{text}"
  }
  while true
    print "> "
    choice = gets.chomp.to_i
    if OPTS.keys.include?(choice)
      case choice
      when 2
        exit
      when 3
        FileUtils.mv(ENV["NEO4J_DB"], "#{ENV["NEO4J_DB"]}_#{Time.now.strftime("%Y%m%d%H%M%S")}")
      when 4
        puts "Deleting: #{ENV["NEO4J_DB"]}"
        FileUtils.rm_r(ENV["NEO4J_DB"])
      end
      break
    end
  end
end

# Load the environment. Order of execution is important
# here because we can't delete/backup the graph once it
# is loaded.
require "ehri"
require "ushmmimporter"
require "icaatomimporter"
require "eagimporter"

include Ehri

class DbBuilder

  def init_db
    puts "Initializing..."
    Commands::Initialize.new.exec(Graph, [].to_java(:string))
  end

  def create_users
    puts "Importing users from YAML..."
    Commands::LoadFixtures.new.exec(Graph, [USER_FIXTURES].to_java(:string))
  end

  def import_repositories
    puts "Importing repository EAG..."
    EagImporter::import(EAG_DATA_DIR, USER_ID)
  end

  def import_corporate_bodies 
    puts "Creating Corporate body set..."
    Commands::EntityAdd.new.exec(Graph, [
      "authoritativeSet",
      "-Pidentifier=ehri-cb",
      '-Pname="Ehri Corporate Bodies"',
      "--user", USER_ID,
      "--log", "Adding authoritative set for EHRI corporate bodies"].to_java(:string))

    puts "Importing ICA-AtoM EAC authorities..."
    auth_xml = Dir.glob("#{DROPBOX}/eac-dump-150413/*xml")
    Commands::EacImport.new.exec(Graph, ([
      "--user", USER_ID,
      "--scope", "ehri-cb",
      "--tolerant",
      "--log", "Importing EHRI corporate bodies",
    ] + auth_xml).to_java(:string))
  end

  def import_personalities
    puts "Creating Personalities set..."
    Commands::EntityAdd.new.exec(Graph, [
      "authoritativeSet",
      "-Pidentifier=ehri-pers",
      '-Pname="Ehri Personalities"',
      "--user", USER_ID,
      "--log", "Adding authoritative set for EHRI personalities"].to_java(:string))

    puts "Importing Personalities CSV..."
    Commands::PersonalitiesImport.new.exec(Graph, [
      "--user", USER_ID,
      "--scope", "ehri-pers",
      "--log", "Importing EHRI personalities from CSV",
      PERS_CSV].to_java(:string))
  end

  def import_wl    
    puts "Importing Wiener Library EAD..."
    wl_xml = Dir.glob("#{WL_EAD_DIR}/*xml")
    wl_args = ["--scope", WL_ID, "--user", USER_ID, "--tolerant", "--log", "Importing AIM25 Wiener Library EAD"] + wl_xml
    Commands::EadImport.new.exec(Graph, wl_args.to_java(:string))
  end

  def import_icaatom
    puts "Importing ICA-Atom EAD..."
    IcaAtomImporter::import(EAD_DATA_DIR, USER_ID)
  end

  def import_ushmm
    puts "Importing USHMM EAD data..."
    UshmmImporter::import(USHMM_DATA_DIR, USHMM_ID, USER_ID)
  end

  def import_thesaurus
    puts "Creating thesaurus set..."
    Commands::EntityAdd.new.exec(Graph, [
      "cvocVocabulary",
      "-Pidentifier=ehri-skos",
      '-Pname=EHRI Skos',
      "--user", USER_ID,
      "--log", "Creating thesaurus vocabulary"].to_java(:string))

    puts "Importing thesaurus..."
    Commands::SkosVocabularyImport.new.exec(Graph, [
      "--scope", "ehri-skos",
      "--user", USER_ID,
      "--log", "Importing EHRI SKOS Concepts",
      "--tolerant",
      THES_RDF].to_java(:string))
  end

  def import_camps
    puts "Creating camps set..."
    Commands::EntityAdd.new.exec(Graph, [
      "cvocVocabulary",
      "-Pidentifier=ehri-camps",
      '-Pname=EHRI Camps',
      "--user", USER_ID,
      "--log", "Creating camps vocabulary"].to_java(:string))

    puts "Importing thesaurus..."
    Commands::SkosVocabularyImport.new.exec(Graph, [
      "--scope", "ehri-camps",
      "--user", USER_ID,
      "--log", "Importing EHRI Camps SKOS",
      "--tolerant",
      CAMPS_RDF].to_java(:string))
  end

  def import_ghettos
    puts "Creating ghettos set..."
    Commands::EntityAdd.new.exec(Graph, [
      "cvocVocabulary",
      "-Pidentifier=ehri-ghettos",
      '-Pname=EHRI Ghettos',
      "--user", USER_ID,
      "--log", "Creating ghettos vocabulary"].to_java(:string))

    puts "Importing thesaurus..."
    Commands::SkosVocabularyImport.new.exec(Graph, [
      "--scope", "ehri-skos",
      "--user", USER_ID,
      "--log", "Importing EHRI Ghettos SKOS",
      "--tolerant",
      GHETTOS_RDF].to_java(:string))
  end
end

start = Time.now
builder = DbBuilder.new
builder.init_db
builder.create_users
builder.import_thesaurus
builder.import_camps
builder.import_ghettos
builder.import_corporate_bodies
builder.import_personalities
builder.import_repositories
builder.import_wl
builder.import_icaatom
builder.import_ushmm

Graph.get_base_graph.shutdown

puts "Import completed in #{Time.at(Time.now - start).gmtime.strftime('%R:%S')}"


