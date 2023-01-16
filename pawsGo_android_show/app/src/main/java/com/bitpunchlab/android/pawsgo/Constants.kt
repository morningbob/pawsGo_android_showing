package com.bitpunchlab.android.pawsgo

val PetTypeMap = HashMap<TypeOfPet, String>().apply {
    this[TypeOfPet.Dog] = "Dog"
    this[TypeOfPet.Cat] = "Cat"
    this[TypeOfPet.Bird] = "Bird"
    this[TypeOfPet.Other] = "Other"
    this[TypeOfPet.All] = "All"
}