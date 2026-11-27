package com.traceon.domain.classification

data class CategoryDef(val name: String, val group: String, val keywords: Map<String, Double>)

object Taxonomy {
    private fun kw(spec: String): Map<String, Double> = spec.split(",").map { it.trim() }.filter { it.isNotEmpty() }.associate {
        val i = it.lastIndexOf(':')
        it.substring(0, i).trim() to it.substring(i + 1).trim().toDouble()
    }

    private const val TECH = "Technology"
    private const val EDU = "Education"
    private const val ENT = "Entertainment"
    private const val OTHER = "Other"

    val categories: List<CategoryDef> = listOf(
        CategoryDef("Artificial Intelligence", TECH, kw("artificial intelligence:3, ai:2, chatgpt:3, gpt:2, llm:3, large language model:3, generative ai:3, openai:2, claude:1, gemini:1, copilot:1.5, prompt engineering:3, neural network:2.5, deep learning:2.5, midjourney:2, stable diffusion:2, chatbot:2, ai agent:3, ai agents:3, agi:2")),
        CategoryDef("Machine Learning", TECH, kw("machine learning:3, tensorflow:2.5, pytorch:2.5, scikit learn:2, sklearn:2, regression:1.5, training data:2, supervised learning:2, unsupervised:1.5, reinforcement learning:3, data science:2, kaggle:2, feature engineering:2, gradient descent:2.5, random forest:2, pandas:1.5, numpy:1.5, ml:1.5, dataset:1")),
        CategoryDef("Software", TECH, kw("software:2, software engineering:3, devops:2.5, docker:2, kubernetes:2.5, git:1.5, github:2, open source:1.5, system design:2.5, microservices:2.5, database:1.5, sql:1.5, api:1.5, backend:1.5, linux:1.5, algorithm:1.5, data structures:2, ci cd:2, aws:1.5, agile:1.5, scrum:1.5")),
        CategoryDef("Programming", TECH, kw("programming:3, coding:2.5, python:2, javascript:2, typescript:2, java:1.5, kotlin:2, rust:1.5, golang:2, compiler:2, recursion:2, leetcode:2.5, debugging:2, oop:2, learn to code:3, php:2, developer:1")),
        CategoryDef("Android", TECH, kw("android:3, jetpack compose:3, android studio:3, gradle:2, apk:2, play store:1.5, termux:2, adb:2, material design:1.5, androidx:2.5, custom rom:2, magisk:2, kotlin:1.5")),
        CategoryDef("Web Development", TECH, kw("web development:3, html:2, css:2, react:2, nextjs:2, next js:2, tailwind:2, frontend:2, front end:2, node js:2, nodejs:2, vue:1.5, angular:1.5, website:1, responsive design:2, vite:2, webpack:2, wordpress:2, web design:2, ui ux:1.5, javascript:1")),
        CategoryDef("Cybersecurity", TECH, kw("cybersecurity:3, cyber security:3, hacking:2.5, ethical hacking:3, penetration testing:3, pentest:3, malware:2.5, ransomware:2.5, phishing:2.5, vulnerability:2, exploit:2, ctf:2.5, kali linux:3, owasp:3, encryption:1.5, firewall:2, vpn:1.5, zero day:2.5, infosec:3, data breach:2, surveillance:1.5, bug bounty:3, security:1.5")),
        CategoryDef("Mathematics", EDU, kw("mathematics:3, math:2.5, maths:2.5, algebra:2.5, calculus:3, geometry:2.5, trigonometry:3, probability:2, statistics:2, equation:2, theorem:2.5, integral:2, derivative:1.5, matrix:1.5, linear algebra:3, number theory:3, arithmetic:2")),
        CategoryDef("Physics", EDU, kw("physics:3, quantum:2.5, relativity:3, gravity:2, thermodynamics:3, electromagnetism:3, newton:1.5, optics:2.5, kinematics:3, mechanics:1.5, astrophysics:3, black hole:2.5, einstein:2")),
        CategoryDef("Chemistry", EDU, kw("chemistry:3, chemical:2, organic chemistry:3, periodic table:3, molecule:2, reaction:1.5, acid:1.5, atom:1.5, titration:3, isotope:2.5, polymer:2")),
        CategoryDef("Biology", EDU, kw("biology:3, dna:2.5, genetics:3, evolution:2.5, photosynthesis:3, anatomy:2.5, ecosystem:2, bacteria:2, organism:2, species:1.5, zoology:3, botany:3, mitosis:3")),
        CategoryDef("History", EDU, kw("history:3, ancient:2, empire:2, medieval:2.5, world war:3, dynasty:2.5, civilization:2.5, historical:2, mughal:3, colonial:2, archaeology:2.5, renaissance:2")),
        CategoryDef("General Knowledge", EDU, kw("general knowledge:3, gk:2.5, trivia:2.5, quiz:2, did you know:2.5, current affairs:2.5, capital of:2, documentary:1.5, how it works:1.5, upsc:2.5")),
        CategoryDef("Comedy", ENT, kw("comedy:3, funny:2.5, stand up:2.5, standup:2.5, prank:2.5, roast:2, sketch:2, jokes:2.5, humor:2.5, hilarious:2.5, skit:2.5, parody:2.5, comedian:3")),
        CategoryDef("Music", ENT, kw("music:3, song:2.5, songs:2.5, lyrics:2.5, album:2.5, official audio:2.5, official music video:3, guitar:2, piano:2, remix:2.5, concert:2, singer:2.5, lofi:2.5, karaoke:2.5")),
        CategoryDef("Movies", ENT, kw("movie:3, movies:3, film:2.5, trailer:3, cinema:2.5, web series:2.5, netflix:2, box office:3, actor:2, actress:2, bollywood:2.5, hollywood:2.5, anime:2.5, tv show:2.5, imdb:2.5")),
        CategoryDef("Gaming", ENT, kw("gaming:3, gameplay:3, game:1.5, games:1.5, minecraft:3, pubg:3, bgmi:3, valorant:3, fortnite:3, gta:3, esports:3, walkthrough:2.5, streamer:2, playstation:2.5, xbox:2.5, steam:1.5, free fire:3, fps:2, gamer:2.5, speedrun:3, gaming laptop:3")),
        CategoryDef("Memes", ENT, kw("meme:3, memes:3, viral:1.5, shitpost:2, relatable:2, dank:2, troll:2")),
        CategoryDef("Business", OTHER, kw("business:3, startup:3, entrepreneur:3, marketing:2.5, sales:1.5, ecommerce:2.5, branding:2, leadership:2, management:1.5, strategy:1.5, founder:2, b2b:2, saas:2.5, revenue:1.5, product management:2.5, seo:2, freelancing:2, pitch deck:3, side hustle:2.5")),
        CategoryDef("Finance", OTHER, kw("finance:3, stock:2, stocks:2, investing:2.5, investment:2, mutual fund:3, crypto:2.5, bitcoin:2.5, ethereum:2.5, trading:2.5, tax:1.5, loan:1.5, banking:2, inflation:2, budget:1.5, forex:3, nifty:3, sensex:3, savings:1.5, credit card:2, personal finance:3, wealth:1.5")),
        CategoryDef("Health", OTHER, kw("health:3, healthy:2, medical:2.5, doctor:2, disease:2.5, symptoms:2.5, mental health:3, nutrition:2.5, diet:2, sleep:1.5, yoga:1.5, meditation:2, therapy:2, wellness:2.5, immunity:2.5, vitamin:2.5, hospital:2, skincare:2, skin care:2")),
        CategoryDef("Fitness", OTHER, kw("fitness:3, workout:3, gym:3, exercise:2.5, bodybuilding:3, protein:1.5, weight loss:2.5, cardio:2.5, muscle:2.5, calisthenics:3, running:1.5, pushups:2.5, squats:2.5, trainer:2, marathon:2")),
        CategoryDef("Travel", OTHER, kw("travel:3, trip:2, tour:2, hotel:2, flight:2, tourism:3, itinerary:3, backpacking:3, destination:2.5, visa:1.5, beach:1.5, mountains:1.5, road trip:2.5, roadtrip:2.5, trek:2.5, trekking:2.5, airbnb:2.5")),
        CategoryDef("News", OTHER, kw("news:3, breaking:2, headlines:2.5, election:2.5, government:1.5, politics:2.5, parliament:2.5, minister:2, live updates:2.5, press conference:2.5, bulletin:2.5")),
        CategoryDef("Lifestyle", OTHER, kw("lifestyle:3, fashion:2.5, outfit:2.5, makeup:2.5, recipe:2.5, cooking:2.5, food:1.5, home decor:2.5, diy:2, productivity:2, morning routine:3, haul:2.5, minimalism:2.5, parenting:2.5")),
        CategoryDef("Science", OTHER, kw("science:3, scientist:2.5, experiment:2, nasa:2.5, space:1.5, astronomy:3, telescope:2.5, research:1.5, isro:2.5, climate:2, universe:2, planet:2, robotics:2.5, invention:2")),
    )

    /** Strong, domain-level hints: (domain suffix, category, weight). */
    val domainHints: List<Triple<String, String, Double>> = listOf(
        Triple("stackoverflow.com", "Programming", 3.0), Triple("developer.android.com", "Android", 4.0),
        Triple("kotlinlang.org", "Programming", 3.0), Triple("github.com", "Software", 2.0),
        Triple("huggingface.co", "Machine Learning", 3.0), Triple("kaggle.com", "Machine Learning", 3.0),
        Triple("w3schools.com", "Web Development", 3.0), Triple("developer.mozilla.org", "Web Development", 3.0),
        Triple("arxiv.org", "Science", 2.0), Triple("imdb.com", "Movies", 3.0),
        Triple("spotify.com", "Music", 3.0), Triple("genius.com", "Music", 3.0),
        Triple("steampowered.com", "Gaming", 3.0), Triple("twitch.tv", "Gaming", 2.0),
        Triple("moneycontrol.com", "Finance", 3.0), Triple("tradingview.com", "Finance", 3.0), Triple("zerodha.com", "Finance", 3.0),
        Triple("tripadvisor.com", "Travel", 3.0), Triple("makemytrip.com", "Travel", 3.0), Triple("booking.com", "Travel", 3.0),
        Triple("webmd.com", "Health", 3.0), Triple("bbc.com", "News", 3.0), Triple("reuters.com", "News", 3.0),
        Triple("ndtv.com", "News", 3.0), Triple("thehindu.com", "News", 3.0), Triple("cnn.com", "News", 3.0),
    )

    val byName: Map<String, CategoryDef> = categories.associateBy { it.name.lowercase() }
    fun groupOf(name: String): String? = byName[name.lowercase()]?.group
}
