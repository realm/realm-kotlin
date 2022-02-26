/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.entities.SampleWithPrimaryKey
import io.realm.entities.StringPropertyWithPrimaryKey
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.query
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableRealmTests {

    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(
                Parent::class,
                Child::class,
                StringPropertyWithPrimaryKey::class,
                SampleWithPrimaryKey::class
            )
        ).path("$tmpDir/default.realm").build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun copyToRealmWithDefaults() {
        realm.writeBlocking { copyToRealm(Parent()) }
        val parents = realm.query<Parent>().find()
        assertEquals(1, parents.size)
        assertEquals("N.N.", parents[0].name)
    }

    @Test
    fun copyToRealmOrUpdate() {
        realm.writeBlocking {
            val obj = StringPropertyWithPrimaryKey()
            copyToRealm(obj.apply { id = "PRIMARY_KEY" })

            obj.apply { this.value = "UPDATED_VALUE" }
            copyToRealmOrUpdate(obj)
        }

        val objects = realm.query<StringPropertyWithPrimaryKey>().find()
        assertEquals(1, objects.size)
        objects[0].run {
            assertEquals("PRIMARY_KEY", id)
            assertEquals("UPDATED_VALUE", value)
        }
    }

    // @Test
    //     public void copyToRealmOrUpdate_stringPrimaryKeyFieldIsNull() {
    //         final long SECONDARY_FIELD_VALUE = 2192841L;
    //         final long SECONDARY_FIELD_UPDATED = 44887612L;
    //         PrimaryKeyAsString nullPrimaryKeyObj = TestHelper.addStringPrimaryKeyObjectToTestRealm(realm, (String) null, SECONDARY_FIELD_VALUE);
    //
    //         RealmResults<PrimaryKeyAsString> result = realm.where(PrimaryKeyAsString.class).findAll();
    //         assertEquals(1, result.size());
    //         assertEquals(null, result.first().getName());
    //         assertEquals(SECONDARY_FIELD_VALUE, result.first().getId());
    //
    //         // Updates objects.
    //         realm.beginTransaction();
    //         nullPrimaryKeyObj.setId(SECONDARY_FIELD_UPDATED);
    //         realm.copyToRealmOrUpdate(nullPrimaryKeyObj);
    //         realm.commitTransaction();
    //
    //         assertEquals(SECONDARY_FIELD_UPDATED, realm.where(PrimaryKeyAsString.class).findFirst().getId());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_boxedBytePrimaryKeyFieldIsNull() {
    //         final String SECONDARY_FIELD_VALUE = "nullBytePrimaryKeyObj";
    //         final String SECONDARY_FIELD_UPDATED = "nullBytePrimaryKeyObjUpdated";
    //         PrimaryKeyAsBoxedByte nullPrimaryKeyObj = TestHelper.addBytePrimaryKeyObjectToTestRealm(realm, (Byte) null, SECONDARY_FIELD_VALUE);
    //
    //         RealmResults<PrimaryKeyAsBoxedByte> result = realm.where(PrimaryKeyAsBoxedByte.class).findAll();
    //         assertEquals(1, result.size());
    //         assertEquals(SECONDARY_FIELD_VALUE, result.first().getName());
    //         assertEquals(null, result.first().getId());
    //
    //         // Updates objects.
    //         realm.beginTransaction();
    //         nullPrimaryKeyObj.setName(SECONDARY_FIELD_UPDATED);
    //         realm.copyToRealmOrUpdate(nullPrimaryKeyObj);
    //         realm.commitTransaction();
    //
    //         assertEquals(SECONDARY_FIELD_UPDATED, realm.where(PrimaryKeyAsBoxedByte.class).findFirst().getName());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_boxedShortPrimaryKeyFieldIsNull() {
    //         final String SECONDARY_FIELD_VALUE = "nullShortPrimaryKeyObj";
    //         final String SECONDARY_FIELD_UPDATED = "nullShortPrimaryKeyObjUpdated";
    //         PrimaryKeyAsBoxedShort nullPrimaryKeyObj = TestHelper.addShortPrimaryKeyObjectToTestRealm(realm, (Short) null, SECONDARY_FIELD_VALUE);
    //
    //         RealmResults<PrimaryKeyAsBoxedShort> result = realm.where(PrimaryKeyAsBoxedShort.class).findAll();
    //         assertEquals(1, result.size());
    //         assertEquals(SECONDARY_FIELD_VALUE, result.first().getName());
    //         assertEquals(null, result.first().getId());
    //
    //         // Updates objects.
    //         realm.beginTransaction();
    //         nullPrimaryKeyObj.setName(SECONDARY_FIELD_UPDATED);
    //         realm.copyToRealmOrUpdate(nullPrimaryKeyObj);
    //         realm.commitTransaction();
    //
    //         assertEquals(SECONDARY_FIELD_UPDATED, realm.where(PrimaryKeyAsBoxedShort.class).findFirst().getName());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_boxedIntegerPrimaryKeyFieldIsNull() {
    //         final String SECONDARY_FIELD_VALUE = "nullIntegerPrimaryKeyObj";
    //         final String SECONDARY_FIELD_UPDATED = "nullIntegerPrimaryKeyObjUpdated";
    //         PrimaryKeyAsBoxedInteger nullPrimaryKeyObj = TestHelper.addIntegerPrimaryKeyObjectToTestRealm(realm, (Integer) null, SECONDARY_FIELD_VALUE);
    //
    //         RealmResults<PrimaryKeyAsBoxedInteger> result = realm.where(PrimaryKeyAsBoxedInteger.class).findAll();
    //         assertEquals(1, result.size());
    //         assertEquals(SECONDARY_FIELD_VALUE, result.first().getName());
    //         assertEquals(null, result.first().getId());
    //
    //         // Updates objects.
    //         realm.beginTransaction();
    //         nullPrimaryKeyObj.setName(SECONDARY_FIELD_UPDATED);
    //         realm.copyToRealmOrUpdate(nullPrimaryKeyObj);
    //         realm.commitTransaction();
    //
    //         assertEquals(SECONDARY_FIELD_UPDATED, realm.where(PrimaryKeyAsBoxedInteger.class).findFirst().getName());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_boxedLongPrimaryKeyFieldIsNull() {
    //         final String SECONDARY_FIELD_VALUE = "nullLongPrimaryKeyObj";
    //         final String SECONDARY_FIELD_UPDATED = "nullLongPrimaryKeyObjUpdated";
    //         PrimaryKeyAsBoxedLong nullPrimaryKeyObj = TestHelper.addLongPrimaryKeyObjectToTestRealm(realm, (Long) null, SECONDARY_FIELD_VALUE);
    //
    //         RealmResults<PrimaryKeyAsBoxedLong> result = realm.where(PrimaryKeyAsBoxedLong.class).findAll();
    //         assertEquals(1, result.size());
    //         assertEquals(SECONDARY_FIELD_VALUE, result.first().getName());
    //         assertEquals(null, result.first().getId());
    //
    //         // Updates objects.
    //         realm.beginTransaction();
    //         nullPrimaryKeyObj.setName(SECONDARY_FIELD_UPDATED);
    //         realm.copyToRealmOrUpdate(nullPrimaryKeyObj);
    //         realm.commitTransaction();
    //
    //         assertEquals(SECONDARY_FIELD_UPDATED, realm.where(PrimaryKeyAsBoxedLong.class).findFirst().getName());
    //     }
    //
    @Test
    fun copyToRealmOrUpdate_noPrimaryKeyField() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                copyToRealmOrUpdate(Parent())
            }
        }
    }

    //     @Test
    //     public void copyToRealmOrUpdate_addNewObjects() {
    //         realm.executeTransaction(new Realm.Transaction() {
    //             @Override
    //             public void execute(Realm realm) {
    //                 PrimaryKeyAsLong obj = new PrimaryKeyAsLong();
    //                 obj.setId(1);
    //                 obj.setName("Foo");
    //                 realm.copyToRealm(obj);
    //
    //                 PrimaryKeyAsLong obj2 = new PrimaryKeyAsLong();
    //                 obj2.setId(2);
    //                 obj2.setName("Bar");
    //                 realm.copyToRealmOrUpdate(obj2);
    //             }
    //         });
    //
    //         assertEquals(2, realm.where(PrimaryKeyAsLong.class).count());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_updateExistingObject() {
    //         realm.executeTransaction(new Realm.Transaction() {
    //             @Override
    //             public void execute(Realm realm) {
    //                 AllTypesPrimaryKey obj = new AllTypesPrimaryKey();
    //                 obj.setColumnString("Foo");
    //                 obj.setColumnLong(1);
    //                 obj.setColumnFloat(1.23F);
    //                 obj.setColumnDouble(1.234D);
    //                 obj.setColumnBoolean(false);
    //                 obj.setColumnBinary(new byte[] {1, 2, 3});
    //                 obj.setColumnDate(new Date(1000));
    //                 obj.setColumnRealmObject(new DogPrimaryKey(1, "Dog1"));
    //                 obj.setColumnRealmList(new RealmList<DogPrimaryKey>(new DogPrimaryKey(2, "Dog2")));
    //                 obj.setColumnBoxedBoolean(true);
    //                 obj.setColumnStringList(new RealmList<>("1"));
    //                 obj.setColumnBooleanList(new RealmList<>(false));
    //                 obj.setColumnBinaryList(new RealmList<>(new byte[] {1}));
    //                 obj.setColumnLongList(new RealmList<>(1L));
    //                 obj.setColumnDoubleList(new RealmList<>(1D));
    //                 obj.setColumnFloatList(new RealmList<>(1F));
    //                 obj.setColumnDateList(new RealmList<>(new Date(1L)));
    //                 realm.copyToRealm(obj);
    //
    //                 AllTypesPrimaryKey obj2 = new AllTypesPrimaryKey();
    //                 obj2.setColumnString("Bar");
    //                 obj2.setColumnLong(1);
    //                 obj2.setColumnFloat(2.23F);
    //                 obj2.setColumnDouble(2.234D);
    //                 obj2.setColumnBoolean(true);
    //                 obj2.setColumnBinary(new byte[] {2, 3, 4});
    //                 obj2.setColumnDate(new Date(2000));
    //                 obj2.setColumnRealmObject(new DogPrimaryKey(3, "Dog3"));
    //                 obj2.setColumnRealmList(new RealmList<DogPrimaryKey>(new DogPrimaryKey(4, "Dog4")));
    //                 obj2.setColumnBoxedBoolean(false);
    //                 obj2.setColumnStringList(new RealmList<>("2", "3"));
    //                 obj2.setColumnBooleanList(new RealmList<>(true, false));
    //                 obj2.setColumnBinaryList(new RealmList<>(new byte[] {2}, new byte[] {3}));
    //                 obj2.setColumnLongList(new RealmList<>(2L, 3L));
    //                 obj2.setColumnDoubleList(new RealmList<>(2D, 3D));
    //                 obj2.setColumnFloatList(new RealmList<>(2F, 3F));
    //                 obj2.setColumnDateList(new RealmList<>(new Date(2L), new Date(3L)));
    //                 realm.copyToRealmOrUpdate(obj2);
    //             }
    //         });
    //
    //         assertEquals(1, realm.where(AllTypesPrimaryKey.class).count());
    //         AllTypesPrimaryKey obj = realm.where(AllTypesPrimaryKey.class).findFirst();
    //
    //         // Checks that the the only element has all its properties updated.
    //         assertEquals("Bar", obj.getColumnString());
    //         assertEquals(1, obj.getColumnLong());
    //         assertEquals(2.23F, obj.getColumnFloat(), 0);
    //         assertEquals(2.234D, obj.getColumnDouble(), 0);
    //         assertEquals(true, obj.isColumnBoolean());
    //         assertArrayEquals(new byte[] {2, 3, 4}, obj.getColumnBinary());
    //         assertEquals(new Date(2000), obj.getColumnDate());
    //         assertEquals("Dog3", obj.getColumnRealmObject().getName());
    //         assertEquals(1, obj.getColumnRealmList().size());
    //         assertEquals("Dog4", obj.getColumnRealmList().get(0).getName());
    //         assertFalse(obj.getColumnBoxedBoolean());
    //         assertEquals(2, obj.getColumnStringList().size());
    //         assertEquals("2", obj.getColumnStringList().get(0));
    //         assertEquals("3", obj.getColumnStringList().get(1));
    //         assertEquals(2, obj.getColumnBooleanList().size());
    //         assertEquals(true, obj.getColumnBooleanList().get(0));
    //         assertEquals(false, obj.getColumnBooleanList().get(1));
    //         assertEquals(2, obj.getColumnBinaryList().size());
    //         assertArrayEquals(new byte[] {2}, obj.getColumnBinaryList().get(0));
    //         assertArrayEquals(new byte[] {3}, obj.getColumnBinaryList().get(1));
    //         assertEquals(2, obj.getColumnLongList().size());
    //         assertEquals((Long) 2L, obj.getColumnLongList().get(0));
    //         assertEquals((Long) 3L, obj.getColumnLongList().get(1));
    //         assertEquals(2, obj.getColumnDoubleList().size());
    //         assertEquals((Double) 2D, obj.getColumnDoubleList().get(0));
    //         assertEquals((Double) 3D, obj.getColumnDoubleList().get(1));
    //         assertEquals(2, obj.getColumnFloatList().size());
    //         assertEquals((Float) 2F, obj.getColumnFloatList().get(0));
    //         assertEquals((Float) 3F, obj.getColumnFloatList().get(1));
    //         assertEquals(2, obj.getColumnDateList().size());
    //         assertEquals(new Date(2L), obj.getColumnDateList().get(0));
    //         assertEquals(new Date(3L), obj.getColumnDateList().get(1));
    //     }
    //
    @Test
    fun copyToRealmOrUpdate_allTypes() {

    }

    //     @Test
    //     public void copyToRealmOrUpdate_overrideOwnList() {
    //         realm.beginTransaction();
    //         AllJavaTypes managedObj = realm.createObject(AllJavaTypes.class, 1);
    //         managedObj.getFieldList().add(managedObj);
    //         AllJavaTypes unmanagedObj = realm.copyFromRealm(managedObj);
    //         unmanagedObj.setFieldList(managedObj.getFieldList());
    //
    //         managedObj = realm.copyToRealmOrUpdate(unmanagedObj);
    //         assertEquals(1, managedObj.getFieldList().size());
    //         assertEquals(1, managedObj.getFieldList().first().getFieldId());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_cyclicObject() {
    //         CyclicTypePrimaryKey oneCyclicType = new CyclicTypePrimaryKey(1);
    //         oneCyclicType.setName("One");
    //         CyclicTypePrimaryKey anotherCyclicType = new CyclicTypePrimaryKey(2);
    //         anotherCyclicType.setName("Two");
    //         oneCyclicType.setObject(anotherCyclicType);
    //         anotherCyclicType.setObject(oneCyclicType);
    //
    //         realm.beginTransaction();
    //         realm.copyToRealm(oneCyclicType);
    //         realm.commitTransaction();
    //
    //         oneCyclicType.setName("Three");
    //         anotherCyclicType.setName("Four");
    //         realm.beginTransaction();
    //         realm.copyToRealmOrUpdate(oneCyclicType);
    //         realm.commitTransaction();
    //
    //         assertEquals(2, realm.where(CyclicTypePrimaryKey.class).count());
    //         assertEquals("Three", realm.where(CyclicTypePrimaryKey.class).equalTo("id", 1).findFirst().getName());
    //     }
    @Test
    fun copyToRealmOrUpdate_cyclicObject() {
        val sample1 = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "One"
        }
        val sample2 = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringField = "Two"
        }
        sample1.child = sample2
        sample2.child = sample1
        realm.writeBlocking {
            copyToRealm(sample1)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("One", stringField)
            child?.run {
                assertEquals(2, primaryKey)
                assertEquals("Two", stringField)
            }
        }

        sample1.stringField = "Three"
        sample2.stringField = "Four"

        realm.writeBlocking {
            copyToRealmOrUpdate(sample1)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("Three", stringField)
            child?.run {
                assertEquals(2, primaryKey)
                assertEquals("Four", stringField)
            }
        }
    }

    //
    //
    //     // Checks that an unmanaged object with only default values can override data.
    //     @Test
    //     public void copyToRealmOrUpdate_defaultValuesOverrideExistingData() {
    //         realm.executeTransaction(new Realm.Transaction() {
    //             @Override
    //             public void execute(Realm realm) {
    //                 AllTypesPrimaryKey obj = new AllTypesPrimaryKey();
    //                 obj.setColumnString("Foo");
    //                 obj.setColumnLong(1);
    //                 obj.setColumnFloat(1.23F);
    //                 obj.setColumnDouble(1.234D);
    //                 obj.setColumnBoolean(false);
    //                 obj.setColumnBinary(new byte[] {1, 2, 3});
    //                 obj.setColumnDate(new Date(1000));
    //                 obj.setColumnRealmObject(new DogPrimaryKey(1, "Dog1"));
    //                 obj.setColumnRealmList(new RealmList<DogPrimaryKey>(new DogPrimaryKey(2, "Dog2")));
    //                 realm.copyToRealm(obj);
    //
    //                 AllTypesPrimaryKey obj2 = new AllTypesPrimaryKey();
    //                 obj2.setColumnLong(1);
    //                 realm.copyToRealmOrUpdate(obj2);
    //             }
    //         });
    //
    //         assertEquals(1, realm.where(AllTypesPrimaryKey.class).count());
    //
    //         AllTypesPrimaryKey obj = realm.where(AllTypesPrimaryKey.class).findFirst();
    //         assertNull(obj.getColumnString());
    //         assertEquals(1, obj.getColumnLong());
    //         assertEquals(0.0F, obj.getColumnFloat(), 0);
    //         assertEquals(0.0D, obj.getColumnDouble(), 0);
    //         assertEquals(false, obj.isColumnBoolean());
    //         assertNull(obj.getColumnBinary());
    //         assertNull(obj.getColumnDate());
    //         assertNull(obj.getColumnRealmObject());
    //         assertEquals(0, obj.getColumnRealmList().size());
    //     }
    //
    //
    //     // Tests that if references to objects are removed, the objects are still in the Realm.
    //     @Test
    //     public void copyToRealmOrUpdate_referencesNotDeleted() {
    //         realm.executeTransaction(new Realm.Transaction() {
    //             @Override
    //             public void execute(Realm realm) {
    //                 AllTypesPrimaryKey obj = new AllTypesPrimaryKey();
    //                 obj.setColumnLong(1);
    //                 obj.setColumnRealmObject(new DogPrimaryKey(1, "Dog1"));
    //                 obj.setColumnRealmList(new RealmList<DogPrimaryKey>(new DogPrimaryKey(2, "Dog2")));
    //                 realm.copyToRealm(obj);
    //
    //                 AllTypesPrimaryKey obj2 = new AllTypesPrimaryKey();
    //                 obj2.setColumnLong(1);
    //                 obj2.setColumnRealmObject(new DogPrimaryKey(3, "Dog3"));
    //                 obj2.setColumnRealmList(new RealmList<DogPrimaryKey>(new DogPrimaryKey(4, "Dog4")));
    //                 realm.copyToRealmOrUpdate(obj2);
    //             }
    //         });
    //
    //         assertEquals(1, realm.where(AllTypesPrimaryKey.class).count());
    //         assertEquals(4, realm.where(DogPrimaryKey.class).count());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_primaryKeyMixInObjectGraph() {
    //         // Crate Object graph where tier 2 consists of 1 object with primary key and one doesn't.
    //         // Tier 3 both have objects with primary keys.
    //         //
    //         //        PK
    //         //     /      \
    //         //    PK      nonPK
    //         //    |        |
    //         //    PK       PK
    //         DogPrimaryKey dog = new DogPrimaryKey(1, "Dog");
    //         OwnerPrimaryKey owner = new OwnerPrimaryKey(1, "Owner");
    //         owner.setDog(dog);
    //
    //         Cat cat = new Cat();
    //         cat.setScaredOfDog(dog);
    //
    //         PrimaryKeyMix mixObject = new PrimaryKeyMix(1);
    //         mixObject.setDogOwner(owner);
    //         mixObject.setCat(cat);
    //
    //         realm.beginTransaction();
    //         PrimaryKeyMix realmObject = realm.copyToRealmOrUpdate(mixObject);
    //         realm.commitTransaction();
    //
    //         assertEquals("Dog", realmObject.getCat().getScaredOfDog().getName());
    //         assertEquals("Dog", realmObject.getDogOwner().getDog().getName());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_iterable() {
    //         realm.executeTransaction(new Realm.Transaction() {
    //             @Override
    //             public void execute(Realm realm) {
    //                 PrimaryKeyAsLong obj = new PrimaryKeyAsLong();
    //                 obj.setId(1);
    //                 obj.setName("Foo");
    //                 realm.copyToRealm(obj);
    //
    //                 PrimaryKeyAsLong obj2 = new PrimaryKeyAsLong();
    //                 obj2.setId(1);
    //                 obj2.setName("Bar");
    //
    //                 PrimaryKeyAsLong obj3 = new PrimaryKeyAsLong();
    //                 obj3.setId(1);
    //                 obj3.setName("Baz");
    //
    //                 realm.copyToRealmOrUpdate(Arrays.asList(obj2, obj3));
    //             }
    //         });
    //
    //         assertEquals(1, realm.where(PrimaryKeyAsLong.class).count());
    //         assertEquals("Baz", realm.where(PrimaryKeyAsLong.class).findFirst().getName());
    //     }
    //
    //     // Tests that a collection of objects with references all gets copied.
    //     @Test
    //     public void copyToRealmOrUpdate_iterableChildObjects() {
    //         DogPrimaryKey dog = new DogPrimaryKey(1, "Snoop");
    //
    //         AllTypesPrimaryKey allTypes1 = new AllTypesPrimaryKey();
    //         allTypes1.setColumnLong(1);
    //         allTypes1.setColumnRealmObject(dog);
    //
    //         AllTypesPrimaryKey allTypes2 = new AllTypesPrimaryKey();
    //         allTypes1.setColumnLong(2);
    //         allTypes2.setColumnRealmObject(dog);
    //
    //         realm.beginTransaction();
    //         realm.copyToRealmOrUpdate(Arrays.asList(allTypes1, allTypes2));
    //         realm.commitTransaction();
    //
    //         assertEquals(2, realm.where(AllTypesPrimaryKey.class).count());
    //         assertEquals(1, realm.where(DogPrimaryKey.class).count());
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_objectInOtherThreadThrows() {
    //         final CountDownLatch bgThreadDoneLatch = new CountDownLatch(1);
    //
    //         realm.beginTransaction();
    //         final OwnerPrimaryKey ownerPrimaryKey = realm.createObject(OwnerPrimaryKey.class, 0);
    //         realm.commitTransaction();
    //
    //         new Thread(new Runnable() {
    //             @Override
    //             public void run() {
    //                 final Realm bgRealm = Realm.getInstance(realm.getConfiguration());
    //                 bgRealm.beginTransaction();
    //                 try {
    //                     bgRealm.copyToRealm(ownerPrimaryKey);
    //                     fail();
    //                 } catch (IllegalArgumentException expected) {
    //                     assertEquals("Objects which belong to Realm instances in other threads cannot be copied into this" +
    //                                     " Realm instance.",
    //                             expected.getMessage());
    //                 }
    //                 bgRealm.cancelTransaction();
    //                 bgRealm.close();
    //                 bgThreadDoneLatch.countDown();
    //             }
    //         }).start();
    //
    //         TestHelper.awaitOrFail(bgThreadDoneLatch);
    //     }
    //
    //     @Test
    //     public void copyToRealmOrUpdate_listHasObjectInOtherThreadThrows() {
    //         final CountDownLatch bgThreadDoneLatch = new CountDownLatch(1);
    //         final OwnerPrimaryKey ownerPrimaryKey = new OwnerPrimaryKey();
    //
    //         realm.beginTransaction();
    //         Dog dog = realm.createObject(Dog.class);
    //         realm.commitTransaction();
    //         ownerPrimaryKey.setDogs(new RealmList<Dog>(dog));
    //
    //         new Thread(new Runnable() {
    //             @Override
    //             public void run() {
    //                 final Realm bgRealm = Realm.getInstance(realm.getConfiguration());
    //                 bgRealm.beginTransaction();
    //                 try {
    //                     bgRealm.copyToRealm(ownerPrimaryKey);
    //                     fail();
    //                 } catch (IllegalArgumentException expected) {
    //                     assertEquals("Objects which belong to Realm instances in other threads cannot be copied into this" +
    //                                     " Realm instance.",
    //                             expected.getMessage());
    //                 }
    //                 bgRealm.cancelTransaction();
    //                 bgRealm.close();
    //                 bgThreadDoneLatch.countDown();
    //             }
    //         }).start();
    //
    //         TestHelper.awaitOrFail(bgThreadDoneLatch);
    //     }
    //

    //     // Test to reproduce issue https://github.com/realm/realm-java/issues/4957
    //     @Test
    //     public void copyToRealmOrUpdate_bug4957() {
    //         Object4957 listElement = new Object4957();
    //         listElement.setId(1);
    //
    //         Object4957 parent = new Object4957();
    //         parent.setId(0);
    //         parent.getChildList().add(listElement);
    //
    //         // parentCopy has same fields as the parent does. But they are not the same object.
    //         Object4957 parentCopy = new Object4957();
    //         parentCopy.setId(0);
    //         parentCopy.getChildList().add(listElement);
    //
    //         parent.setChild(parentCopy);
    //         parentCopy.setChild(parentCopy);
    //
    //         realm.beginTransaction();
    //         Object4957 managedParent = realm.copyToRealmOrUpdate(parent);
    //         realm.commitTransaction();
    //         // The original bug fails here. It resulted the listElement has been added to the list twice.
    //         // Because of the parent and parentCopy are not the same object, proxy will miss the cache to know the object
    //         // has been created before. But it does know they share the same PK value.
    //         assertEquals(1, managedParent.getChildList().size());
    //
    //         // insertOrUpdate doesn't have the problem!
    //         realm.beginTransaction();
    //         realm.deleteAll();
    //         realm.insertOrUpdate(parent);
    //         realm.commitTransaction();
    //         managedParent = realm.where(Object4957.class).findFirst();
    //         assertEquals(1, managedParent.getChildList().size());
    //     }
    @Test
    fun copyToRealmOrUpdate_realmJavaBug4957() {
        val parent = SampleWithPrimaryKey().apply {
            primaryKey = 0

            val listElement = SampleWithPrimaryKey().apply { primaryKey = 1 }
            objectListField.add(listElement)

            child = SampleWithPrimaryKey().apply {
                primaryKey = 0
                objectListField.add(listElement)
                child = this
            }
        }
        realm.writeBlocking {
            copyToRealmOrUpdate(parent)
        }.run {
            assertEquals(1, objectListField.size)
        }
    }

    @Test
    fun writeReturningUnmanaged() {
        assertTrue(realm.writeBlocking { Parent() } is Parent)
    }

    @Test
    fun cancelingWrite() {
        assertEquals(0, realm.query<Parent>().find().size)
        realm.writeBlocking {
            copyToRealm(Parent())
            cancelWrite()
        }
        assertEquals(0, realm.query<Parent>().count().find())
    }

    @Test
    fun cancellingWriteTwiceThrows() {
        realm.writeBlocking {
            cancelWrite()
            assertFailsWith<IllegalStateException> {
                cancelWrite()
            }
        }
    }

    @Test
    fun findLatest_basic() {
        val instance = realm.writeBlocking { copyToRealm(StringPropertyWithPrimaryKey()) }

        realm.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
        }
    }

    @Test
    fun findLatest_updated() {
        val updatedValue = "UPDATED"
        val instance = realm.writeBlocking { copyToRealm(StringPropertyWithPrimaryKey()) }
        assertNull(instance.value)

        realm.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
            latest.value = updatedValue
        }
        assertNull(instance.value)

        realm.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
            assertEquals(updatedValue, latest.value)
        }
    }

    @Test
    fun findLatest_deleted() {
        val instance = realm.writeBlocking { copyToRealm(StringPropertyWithPrimaryKey()) }

        realm.writeBlocking {
            findLatest(instance)?.let {
                delete(it)
            }
        }
        realm.writeBlocking {
            assertNull(findLatest(instance))
        }
    }

    @Test
    fun findLatest_identityForLiveObject() {
        realm.writeBlocking {
            val instance = copyToRealm(StringPropertyWithPrimaryKey())
            val latest = findLatest(instance)
            assert(instance === latest)
        }
    }

    @Test
    fun findLatest_unmanagedThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                val latest = findLatest(StringPropertyWithPrimaryKey())
            }
        }
    }

    @Test
    fun findLatest_inLongHistory() {
        runBlocking {
            val child = realm.write { copyToRealm(Child()) }
            for (i in 1..10) {
                realm.write {
                    assertNotNull(findLatest(child))
                }
                delay(100)
            }
        }
    }

    @Test
    fun delete() {
        realm.writeBlocking {
            val liveObject = copyToRealm(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
        }
    }

    @Test
    fun delete_deletedObjectThrows() {
        realm.writeBlocking {
            val liveObject = copyToRealm(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
            assertFailsWith<IllegalArgumentException> {
                delete(liveObject)
            }
        }
    }

    @Test
    fun delete_unmanagedObjectsThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                delete(Parent())
            }
        }
    }

    @Test
    fun delete_frozenObjectsThrows() {
        val frozenObj = realm.writeBlocking {
            copyToRealm(Parent())
        }
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                delete(frozenObj)
            }
        }
    }
}
